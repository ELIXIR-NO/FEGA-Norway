// Package pg performs the post-finalize database verification: connect to the
// SDA Postgres over mutual TLS (verify-full) and look up the finalized row. The
// query is single-shot with no retry, a candidate for bounded polling later.
package pg

import (
	"context"
	"crypto"
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"errors"
	"fmt"
	"os"

	"github.com/jackc/pgx/v5"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/certs"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/config"
)

const query = "select archive_path,stable_id from local_ega.files where status = 'READY' AND inbox_path = $1"

// VerifyFinalized looks up the READY row for inboxPath and returns its
// archive_path and stable_id. It errors if no such row exists.
func VerifyFinalized(ctx context.Context, cfg *config.Config, inboxPath string) (archivePath, stableID string, err error) {
	tlsConf, err := buildTLSConfig(cfg)
	if err != nil {
		return "", "", err
	}

	connConfig, err := pgx.ParseConfig(fmt.Sprintf(
		"host=%s port=%s dbname=%s user=%s password=%s application_name=LocalEGA sslmode=disable",
		cfg.SdaDbHost, cfg.SdaDbPort, cfg.SdaDbDatabaseName, cfg.SdaDbUsername, cfg.SdaDbPassword))
	if err != nil {
		return "", "", fmt.Errorf("parsing pg config: %w", err)
	}
	// sslmode=disable above only suppresses pgx's built-in TLS setup; setting
	// TLSConfig directly makes the connection verify-full with our client cert
	// and the mkcert CA.
	connConfig.TLSConfig = tlsConf
	connConfig.Fallbacks = nil

	conn, err := pgx.ConnectConfig(ctx, connConfig)
	if err != nil {
		return "", "", fmt.Errorf("connecting to SDA db: %w", err)
	}
	defer conn.Close(ctx)

	err = conn.QueryRow(ctx, query, inboxPath).Scan(&archivePath, &stableID)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", "", fmt.Errorf("verification failed: no READY row for inbox_path %q", inboxPath)
	}
	if err != nil {
		return "", "", fmt.Errorf("querying local_ega.files: %w", err)
	}
	return archivePath, stableID, nil
}

// buildTLSConfig assembles a verify-full client mTLS config from the staged
// client.pem, client-key.der and rootCA.pem. The client key is distributed in
// DER form, so it is parsed into a tls.Certificate here.
func buildTLSConfig(cfg *config.Config) (*tls.Config, error) {
	clientPEMPath, err := certs.CertFile("client.pem")
	if err != nil {
		return nil, err
	}
	clientKeyPath, err := certs.CertFile("client-key.der")
	if err != nil {
		return nil, err
	}
	pool, err := certs.LoadRootCAPool()
	if err != nil {
		return nil, err
	}

	clientPEM, err := os.ReadFile(clientPEMPath)
	if err != nil {
		return nil, err
	}
	leaf, _ := pem.Decode(clientPEM)
	if leaf == nil {
		return nil, fmt.Errorf("no PEM block in client.pem")
	}
	keyDER, err := os.ReadFile(clientKeyPath)
	if err != nil {
		return nil, err
	}
	key, err := parseDERKey(keyDER)
	if err != nil {
		return nil, fmt.Errorf("parsing client-key.der: %w", err)
	}

	return &tls.Config{
		Certificates: []tls.Certificate{{Certificate: [][]byte{leaf.Bytes}, PrivateKey: key}},
		RootCAs:      pool,
		ServerName:   cfg.SdaDbHost, // verify-full: hostname must match the cert SAN
	}, nil
}

func parseDERKey(der []byte) (crypto.PrivateKey, error) {
	if k, err := x509.ParsePKCS8PrivateKey(der); err == nil {
		return k, nil
	}
	if k, err := x509.ParseECPrivateKey(der); err == nil {
		return k, nil
	}
	if k, err := x509.ParsePKCS1PrivateKey(der); err == nil {
		return k, nil
	}
	return nil, fmt.Errorf("unsupported DER private key format (tried PKCS#8, EC, PKCS#1)")
}
