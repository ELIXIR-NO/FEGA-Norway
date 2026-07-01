// Package token mints the GA4GH visa JWTs the suite needs. The JWT is hand-built
// with the standard library to keep exact control over the header and claim
// shape the proxy validates: a single-string aud, second-granularity timestamps,
// and the nested ga4gh_visa_v1 map. Signing is RS256 (RSASSA-PKCS1-v1_5 over
// SHA-256).
package token

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"os"
	"strings"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/certs"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/constants"
)

// GenerateVisaToken builds and signs a GA4GH visa token for resource, signing
// with the RSA key at privateKeyPath.
//
// resource fills the {dataset} slot of the visa value claim
// (https://ega.tsd.usit.uio.no/datasets/<resource>/). For download it is a real
// dataset id (EGAD...). For upload and inbox-listing it is the literal string
// "upload": those flows only need a structurally valid signed visa to
// authenticate, so the grant points at a dataset that does not exist.
// TODO: hoist these scope strings into named constants.
func GenerateVisaToken(cfg *config.Config, resource, privateKeyPath string) (string, error) {
	priv, err := loadPrivateKey(cfg, privateKeyPath)
	if err != nil {
		return "", err
	}

	var subject, audience string
	if cfg.LSAAIToken != "" && cfg.Integration == config.IntegrationEgaDev {
		sub, aud, err := ExtractLSAAIDetails(cfg.LSAAIToken)
		if err != nil {
			return "", err
		}
		subject, audience = sub, aud
	} else {
		subject = constants.JWTSubject
		audience = cfg.ProxyTokenAudience
	}

	return signVisa(priv, subject, audience, resource)
}

// MintForgedVisa builds a structurally-valid visa signed with a freshly
// generated, untrusted RSA key (resource "download").
func MintForgedVisa(cfg *config.Config) (string, error) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return "", err
	}
	return signVisa(priv, constants.JWTSubject, cfg.ProxyTokenAudience, "download")
}

// signVisa assembles the shared header/claims and signs them.
func signVisa(priv *rsa.PrivateKey, subject, audience, resource string) (string, error) {
	header := map[string]any{
		"jku": constants.JWTJku,
		"kid": constants.JWTKid,
		"typ": constants.JWTTyp,
		"alg": constants.JWTAlg,
	}
	ga4ghVisa := map[string]any{
		"asserted": constants.VisaAsserted,
		"by":       constants.VisaBy,
		"source":   constants.VisaSource,
		"type":     constants.VisaType,
		"value":    fmt.Sprintf(constants.VisaValueTemplate, resource),
	}
	claims := map[string]any{
		"sub":           subject,
		"aud":           audience, // scalar, not an array: the proxy expects a single-string audience
		"iss":           constants.JWTIssuer,
		"exp":           constants.JWTExpiration,
		"iat":           constants.JWTIssuedAt,
		"jti":           constants.JWTID,
		"ga4gh_visa_v1": ga4ghVisa,
	}
	return signRS256(header, claims, priv)
}

func signRS256(header, claims map[string]any, priv *rsa.PrivateKey) (string, error) {
	headerJSON, err := json.Marshal(header)
	if err != nil {
		return "", err
	}
	claimsJSON, err := json.Marshal(claims)
	if err != nil {
		return "", err
	}
	enc := base64.RawURLEncoding
	signingInput := enc.EncodeToString(headerJSON) + "." + enc.EncodeToString(claimsJSON)
	digest := sha256.Sum256([]byte(signingInput))
	sig, err := rsa.SignPKCS1v15(rand.Reader, priv, crypto.SHA256, digest[:])
	if err != nil {
		return "", err
	}
	return signingInput + "." + enc.EncodeToString(sig), nil
}

// ExtractLSAAIDetails base64url-decodes the JWT payload (without verifying the
// signature) and returns its sub and aud claims. It is used for the EGA_DEV
// flow, where the real LS-AAI token supplies the subject and audience.
func ExtractLSAAIDetails(jwt string) (sub, aud string, err error) {
	parts := strings.Split(jwt, ".")
	if len(parts) < 2 {
		return "", "", fmt.Errorf("malformed JWT: expected at least 2 segments, got %d", len(parts))
	}
	payload, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(parts[1], "="))
	if err != nil {
		return "", "", fmt.Errorf("decoding JWT payload: %w", err)
	}
	var claims struct {
		Sub string `json:"sub"`
		Aud string `json:"aud"`
	}
	if err := json.Unmarshal(payload, &claims); err != nil {
		return "", "", fmt.Errorf("parsing JWT payload: %w", err)
	}
	return claims.Sub, claims.Aud, nil
}

// loadPrivateKey resolves the key path (absolute path under EGA_DEV, otherwise
// /storage/certs) and parses it.
func loadPrivateKey(cfg *config.Config, path string) (*rsa.PrivateKey, error) {
	resolved, err := resolveKeyPath(cfg, path)
	if err != nil {
		return nil, err
	}
	pemBytes, err := os.ReadFile(resolved)
	if err != nil {
		return nil, err
	}
	return ParseRSAPrivateKeyPEM(pemBytes)
}

func resolveKeyPath(cfg *config.Config, path string) (string, error) {
	if cfg.Integration == config.IntegrationEgaDev {
		return certs.File(path)
	}
	return certs.CertFile(path)
}

// ParseRSAPrivateKeyPEM parses a PEM-encoded RSA private key in either PKCS#1
// ("RSA PRIVATE KEY") or PKCS#8 ("PRIVATE KEY") form.
func ParseRSAPrivateKeyPEM(pemBytes []byte) (*rsa.PrivateKey, error) {
	block, _ := pem.Decode(pemBytes)
	if block == nil {
		return nil, fmt.Errorf("no PEM block found in private key")
	}
	if key, err := x509.ParsePKCS1PrivateKey(block.Bytes); err == nil {
		return key, nil
	}
	parsed, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("parsing private key (tried PKCS#1 and PKCS#8): %w", err)
	}
	rsaKey, ok := parsed.(*rsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("private key is not RSA (got %T)", parsed)
	}
	return rsaKey, nil
}

// ParseRSAPublicKeyPEM parses a PEM-encoded RSA public key in either PKIX
// ("PUBLIC KEY") or PKCS#1 ("RSA PUBLIC KEY") form.
func ParseRSAPublicKeyPEM(pemBytes []byte) (*rsa.PublicKey, error) {
	block, _ := pem.Decode(pemBytes)
	if block == nil {
		return nil, fmt.Errorf("no PEM block found in public key")
	}
	if parsed, err := x509.ParsePKIXPublicKey(block.Bytes); err == nil {
		rsaKey, ok := parsed.(*rsa.PublicKey)
		if !ok {
			return nil, fmt.Errorf("public key is not RSA (got %T)", parsed)
		}
		return rsaKey, nil
	}
	key, err := x509.ParsePKCS1PublicKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("parsing public key (tried PKIX and PKCS#1): %w", err)
	}
	return key, nil
}
