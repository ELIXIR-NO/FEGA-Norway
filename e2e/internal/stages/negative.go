package stages

import (
	"context"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/token"
)

// MissingBasicAuth sends a valid visa without basic authentication and
// verifies that the protected endpoint rejects the request.
func MissingBasicAuth(ctx context.Context, s *state.State) error {
	s.Log.Info("sending request without basic authentication")

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
		return fmt.Errorf("sending request without basic authentication: %w", err)
	}

	rejected := res.Status == 400 || res.Status == 401 || res.Status == 403
	s.Log.Check(
		fmt.Sprintf("request without basic authentication rejected (HTTP %d)", res.Status),
		rejected,
	)

	return check.True(
		rejected,
		fmt.Sprintf(
			"request without basic authentication must be rejected by /files; got HTTP %d. Body: %s",
			res.Status,
			res.Body,
		),
	)
}

// MissingBearerToken calls a protected endpoint without a bearer token and
// verifies that the proxy rejects the request with HTTP 400, 401 or 403.
func MissingBearerToken(ctx context.Context, s *state.State) error {
	s.Log.Info("sending request without a bearer token")

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
		return fmt.Errorf("sending request without bearer token: %w", err)
	}

	rejected := res.Status == 400 || res.Status == 401 || res.Status == 403
	s.Log.Check(
		fmt.Sprintf("request without bearer token rejected (HTTP %d)", res.Status),
		rejected,
	)

	return check.True(
		rejected,
		fmt.Sprintf(
			"request without bearer token must be rejected by /files; got HTTP %d. Body: %s",
			res.Status,
			res.Body,
		),
	)
}

// WrongSigningKey sends a structurally-valid visa signed with an
// untrusted key and asserts the proxy rejects it with 401/403. A 2xx means the
// proxy trusted an unverified token (audit finding C1 live).
//
// The proxy's own unit tests cover the decoder in isolation (#836); this stage
// covers the part they cannot, that the check is actually reached on a request
// to a live endpoint. See #854 for the wider red suite this belongs to.
func WrongSigningKey(ctx context.Context, s *state.State) error {
	s.Log.Info("C1 negative check: sending JWT signed by an untrusted key")
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
	s.Log.Check(fmt.Sprintf("forged visa rejected (HTTP %d)", res.Status), rejected)
	return check.True(rejected,
		fmt.Sprintf("forged-signature JWT must be rejected by /files but got HTTP %d. "+
			"Body: %s. A 2xx here means C1 is live: the proxy trusted an unverified token.",
			res.Status, res.Body))
}
