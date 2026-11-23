package stages

import (
	"context"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/token"
)

// MissingCegaCredentials sends a valid visa without CEGA credentials and
// verifies that the proxy refuses the request.
func MissingCegaCredentials(ctx context.Context, s *state.State) error {
	s.Log.Info("sending request without CEGA credentials")

	visa, err := resolveUploadToken(s)
	if err != nil {
		return fmt.Errorf("minting visa: %w", err)
	}

	url := fmt.Sprintf(
		"https://%s:%s/files?inbox=true",
		s.Config.ProxyHost,
		s.Config.ProxyPort,
	)

	res, err := httpx.New(s.Config).Do(
		ctx,
		"GET",
		url,
		httpx.WithProxyBearer(visa),
	)
	if err != nil {
		return fmt.Errorf("sending request without CEGA credentials: %w", err)
	}

	rejected := res.Status == 400 || res.Status == 401 || res.Status == 403
	s.Log.Check(
		fmt.Sprintf("request without CEGA credentials rejected (HTTP %d)", res.Status),
		rejected,
	)

	return check.True(
		rejected,
		fmt.Sprintf(
			"request without CEGA credentials must be rejected by /files; got HTTP %d. Body: %s",
			res.Status,
			res.Body,
		),
	)
}

// MissingVisa sends valid CEGA credentials without a GA4GH visa and verifies
// that the proxy refuses the request with HTTP 400, 401 or 403.
func MissingVisa(ctx context.Context, s *state.State) error {
	s.Log.Info("sending request without a GA4GH visa")

	url := fmt.Sprintf(
		"https://%s:%s/files?inbox=true",
		s.Config.ProxyHost,
		s.Config.ProxyPort,
	)

	res, err := httpx.New(s.Config).Do(
		ctx,
		"GET",
		url,
		httpx.WithBasicAuth(
			s.Config.CegaAuthUsername,
			s.Config.CegaAuthPassword,
		),
	)
	if err != nil {
		return fmt.Errorf("sending request without a GA4GH visa: %w", err)
	}

	rejected := res.Status == 400 || res.Status == 401 || res.Status == 403
	s.Log.Check(
		fmt.Sprintf("request without a GA4GH visa rejected (HTTP %d)", res.Status),
		rejected,
	)

	return check.True(
		rejected,
		fmt.Sprintf(
			"request without a GA4GH visa must be rejected by /files; got HTTP %d. Body: %s",
			res.Status,
			res.Body,
		),
	)
}

// UntrustedVisaIssuer sends valid CEGA credentials with a structurally valid
// GA4GH visa signed by an untrusted issuer and verifies that the proxy refuses it.
func UntrustedVisaIssuer(ctx context.Context, s *state.State) error {
	s.Log.Info("sending GA4GH visa signed by an untrusted issuer")
	forged, err := token.MintForgedVisa(s.Config)
	if err != nil {
		return err
	}
	url := fmt.Sprintf("https://%s:%s/files?inbox=true", s.Config.ProxyHost, s.Config.ProxyPort)

	res, err := httpx.New(s.Config).Do(ctx, "GET", url,
		httpx.WithBasicAuth(s.Config.CegaAuthUsername, s.Config.CegaAuthPassword),
		httpx.WithProxyBearer(forged))
	if err != nil {
		return err
	}
	rejected := res.Status == 401 || res.Status == 403
	s.Log.Check(fmt.Sprintf("untrusted visa issuer rejected (HTTP %d)", res.Status), rejected)
	return check.True(rejected,
		fmt.Sprintf("a visa from an untrusted issuer must be rejected by /files but got HTTP %d. "+
			"Body: %s. A 2xx here means the proxy trusted an unverified token.",
			res.Status, res.Body))
}
