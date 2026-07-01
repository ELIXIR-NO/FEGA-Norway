package stages

import (
	"bytes"
	"context"
	"crypto/ecdh"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/c4gh"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/common"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/constants"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/token"
)

// DownloadViaFegaExportRequest runs the EGA_DEV download: request a FEGA export,
// poll the outbox listing, download via the proxy, decrypt and verify checksums.
// The export payload carries the recipient X25519 key as base64 SPKI DER (see
// buildFegaExportPayload); that encoding is not yet verified against live egadev.
func DownloadViaFegaExportRequest(ctx context.Context, s *state.State) error {
	visaToken, err := token.GenerateVisaToken(s.Config, s.DatasetID, s.Config.EgaDevJwtPrivKeyPath)
	if err != nil {
		return err
	}
	passportToken := s.Config.LSAAIToken
	client := httpx.New()

	// 1) FEGA export request
	payload, err := buildFegaExportPayload(s, visaToken)
	if err != nil {
		return err
	}
	exportURL := fmt.Sprintf("https://%s:%s/export/fega", s.Config.ProxyHost, s.Config.ProxyPort)
	exportRes, err := client.Do(ctx, "POST", exportURL,
		httpx.WithBody([]byte(payload), "application/json"),
		httpx.WithBasicAuth(s.Config.ProxyAdminUsername, s.Config.ProxyAdminPassword))
	if err != nil {
		return err
	}
	if err := check.Equal(exportRes.Status, 200, "FEGA export request"); err != nil {
		return err
	}

	// 2) poll the outbox listing until the file appears
	status, listing, err := checkFilesWithRetry(ctx, s, client, passportToken)
	if err != nil {
		return err
	}
	if listing == nil {
		return check.Failf("no files listed after %d attempts", s.Config.ExportRequestMaxRetries)
	}
	if err := check.Equal(status, 200, "outbox listing status"); err != nil {
		return err
	}
	if err := check.False(len(listing.Files) == 0, "outbox listing is empty"); err != nil {
		return err
	}
	matches := 0
	for _, f := range listing.Files {
		if f.FileName == s.EncName() {
			matches++
		}
	}
	if err := check.Equal(matches, 1, "exactly one outbox file matching the upload"); err != nil {
		return err
	}

	// 3) download via the proxy and validate the file on disk
	basedir := s.Config.EgaDevBaseDirectory
	if basedir != "" && basedir[len(basedir)-1] != '/' {
		basedir += "/"
	}
	outPath := basedir + "out/" + s.EncName()
	downloadURL := fmt.Sprintf("https://%s:%s/stream/%s", s.Config.ProxyHost, s.Config.ProxyPort, s.EncName())
	dlRes, err := client.Do(ctx, "GET", downloadURL, httpx.WithProxyBearer(passportToken))
	if err != nil {
		return err
	}
	if err := check.Equal(dlRes.Status, 200, "proxy download"); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(outPath), 0o755); err != nil {
		return err
	}
	if err := os.WriteFile(outPath, dlRes.Body, 0o644); err != nil {
		return err
	}
	info, err := os.Stat(outPath)
	if err != nil {
		return check.Failf("downloaded file should exist: %v", err)
	}
	if err := check.True(info.Mode().IsRegular(), "downloaded file should be regular"); err != nil {
		return err
	}
	if err := check.True(info.Size() > 0, "downloaded file should not be empty"); err != nil {
		return err
	}

	// 4) decrypt and verify checksums
	s.Log.Info("decrypting the downloaded file")
	decrypted, err := c4gh.Decrypt(bytes.NewReader(dlRes.Body), s.Recipient.Private)
	if err != nil {
		return fmt.Errorf("decrypting downloaded file: %w", err)
	}
	if err := check.Equal(common.Sha256HexBytes(decrypted), s.RawSHA256, "decrypted SHA256 matches original"); err != nil {
		return err
	}
	return check.Equal(common.Md5HexBytes(decrypted), s.RawMD5, "decrypted MD5 matches original")
}

// checkFilesWithRetry polls GET /files?inbox=false until files appear, the
// request fails, or the retries are exhausted (in which case listing is nil).
func checkFilesWithRetry(ctx context.Context, s *state.State, client *httpx.Client, accessToken string) (int, *fileListing, error) {
	listURL := fmt.Sprintf("https://%s:%s/files?inbox=false", s.Config.ProxyHost, s.Config.ProxyPort)
	maxRetries := s.Config.ExportRequestMaxRetries
	intervalSec := int(s.Config.ExportRequestIntervalInSeconds)
	s.Log.Info("waiting before the initial outbox listing call", "seconds", intervalSec)
	common.WaitForProcessing(intervalSec * 1000)

	for i := 1; i <= maxRetries; i++ {
		res, err := client.Do(ctx, "GET", listURL, httpx.WithProxyBearer(accessToken))
		if err != nil {
			return 0, nil, err
		}
		if res.Status != 200 {
			return res.Status, &fileListing{}, nil
		}
		var listing fileListing
		if err := json.Unmarshal(res.Body, &listing); err != nil {
			return 0, nil, fmt.Errorf("parsing outbox listing: %w", err)
		}
		if len(listing.Files) > 0 {
			s.Log.Info("files found", "attempt", i)
			return res.Status, &listing, nil
		}
		if i < maxRetries {
			common.WaitForProcessing(intervalSec * 1000)
		}
	}
	s.Log.Warn("no files found after retries", "attempts", maxRetries)
	return 0, nil, nil
}

// buildFegaExportPayload builds the /export/fega body, encoding the recipient
// X25519 public key as base64-encoded SPKI DER.
func buildFegaExportPayload(s *state.State, visaToken string) (string, error) {
	pub, err := ecdh.X25519().NewPublicKey(s.Recipient.Public[:])
	if err != nil {
		return "", err
	}
	spki, err := x509.MarshalPKIXPublicKey(pub)
	if err != nil {
		return "", err
	}
	b64 := base64.StdEncoding.EncodeToString(spki)
	return fmt.Sprintf(constants.ExportReqBodyFEGA, s.DatasetID, visaToken, b64, "DATASET_ID"), nil
}
