package stages

import (
	"context"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/token"
)

// C1JwtSignatureVerification sends a structurally-valid visa signed with an
// untrusted key and asserts the proxy rejects it with 401/403. A 2xx means the
// proxy trusted an unverified token (audit finding C1 live).
func C1JwtSignatureVerification(ctx context.Context, s *state.State) error {
	s.Log.Info("C1 negative check: sending JWT signed by an untrusted key")
	forged, err := token.MintForgedVisa(s.Config)
	if err != nil {
		return err
	}
	url := fmt.Sprintf("https://%s:%s/files?inbox=true", s.Config.ProxyHost, s.Config.ProxyPort)

	res, err := httpx.New().Do(ctx, "GET", url,
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
