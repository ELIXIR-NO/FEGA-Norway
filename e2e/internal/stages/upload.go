package stages

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/common"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/report"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/token"
)

// UploadViaLegaCmd uploads the encrypted file by invoking the lega-commander
// CLI as a subprocess, asserting a zero exit code.
func UploadViaLegaCmd(ctx context.Context, s *state.State) error {
	const legaCommanderTimeout = 2 * time.Minute
	s.Log.Info("uploading a file via lega-commander CLI")
	tok, err := resolveUploadToken(s)
	if err != nil {
		return err
	}

	encAbs, err := filepath.Abs(s.EncFile)
	if err != nil {
		return err
	}
	instanceURL := fmt.Sprintf("https://%s:%s", s.Config.ProxyHost, s.Config.ProxyPort)

	cctx, cancel := context.WithTimeout(ctx, legaCommanderTimeout)
	defer cancel()
	cmd := exec.CommandContext(cctx, "/usr/local/bin/lega-commander", "upload", "-f", encAbs)
	cmd.Env = append(os.Environ(),
		"LOCAL_EGA_INSTANCE_URL="+instanceURL,
		"CENTRAL_EGA_USERNAME="+s.Config.CegaAuthUsername,
		"CENTRAL_EGA_PASSWORD="+s.Config.CegaAuthPassword,
		"ELIXIR_AAI_TOKEN="+tok,
		"LEGA_COMMANDER_TLS_SKIP_VERIFY=true",
	)

	out, runErr := cmd.CombinedOutput()
	s.Log.Block("lega-commander", report.SanitizeLega(out))
	if errors.Is(cctx.Err(), context.DeadlineExceeded) {
		return fmt.Errorf("lega-commander process timed out after %s", legaCommanderTimeout)
	}
	if runErr != nil {
		if exitErr, ok := errors.AsType[*exec.ExitError](runErr); ok {
			return check.Failf("lega-commander upload failed (exit %d) with output:\n%s", exitErr.ExitCode(), out)
		}
		return fmt.Errorf("running lega-commander: %w", runErr)
	}
	s.Log.Check("lega-commander upload (exit 0)", true)
	return nil
}

// UploadThroughProxy uploads via the proxy's resumable PATCH endpoint and
// asserts a 201 finalize status. Deprecated EGA_DEV path.
func UploadThroughProxy(ctx context.Context, s *state.State) error {
	s.Log.Info("uploading a file through the proxy")
	tok, err := resolveUploadToken(s)
	if err != nil {
		return err
	}

	encBytes, err := os.ReadFile(s.EncFile)
	if err != nil {
		return err
	}
	info, err := os.Stat(s.EncFile)
	if err != nil {
		return err
	}
	md5Hex, err := common.Md5Hex(s.EncFile)
	if err != nil {
		return err
	}

	client := httpx.New()
	uploadURL := fmt.Sprintf("https://%s:%s/stream/%s?md5=%s",
		s.Config.ProxyHost, s.Config.ProxyPort, s.EncName(), md5Hex)
	res, err := client.Do(ctx, "PATCH", uploadURL,
		httpx.WithBasicAuth(s.Config.CegaAuthUsername, s.Config.CegaAuthPassword),
		httpx.WithProxyBearer(tok),
		httpx.WithBody(encBytes, ""))
	if err != nil {
		return err
	}
	var uploaded struct {
		ID string `json:"id"`
	}
	if err := json.Unmarshal(res.Body, &uploaded); err != nil {
		return fmt.Errorf("parsing upload response: %w", err)
	}
	s.Log.Info("upload id", "id", uploaded.ID)

	finalizeURL := fmt.Sprintf("https://%s:%s/stream/%s?uploadId=%s&chunk=end&sha256=%s&fileSize=%d",
		s.Config.ProxyHost, s.Config.ProxyPort, s.EncName(), uploaded.ID, s.EncSHA256, info.Size())
	finRes, err := client.Do(ctx, "PATCH", finalizeURL,
		httpx.WithBasicAuth(s.Config.CegaAuthUsername, s.Config.CegaAuthPassword),
		httpx.WithProxyBearer(tok))
	if err != nil {
		return err
	}
	var finalized struct {
		StatusCode int `json:"statusCode"`
	}
	if err := json.Unmarshal(finRes.Body, &finalized); err != nil {
		return fmt.Errorf("parsing finalize response: %w", err)
	}
	return check.Equal(finalized.StatusCode, 201, "proxy finalize must return statusCode 201")
}

// resolveUploadToken returns a minted visa when no LS-AAI token is provided,
// otherwise the provided token (only valid under EGA_DEV).
func resolveUploadToken(s *state.State) (string, error) {
	if s.Config.LSAAIToken == "" {
		return token.GenerateVisaToken(s.Config, "upload", "jwt.priv.pem")
	}
	if s.Config.Integration != config.IntegrationEgaDev {
		return "", check.Failf("Life Science AAI token provided but the runtime is not set to EGA_DEV")
	}
	return s.Config.LSAAIToken, nil
}
