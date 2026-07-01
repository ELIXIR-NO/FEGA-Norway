package stages

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/common"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/token"
)

// fileListing holds the subset of the proxy's file-listing response the
// assertions read.
type fileListing struct {
	Files []struct {
		FileName string `json:"fileName"`
	} `json:"files"`
	Page string `json:"page"`
}

// InboxCleanup verifies the mapper removed the uploaded file from the inbox,
// polling the proxy's GET /files?inbox=true listing until the file is gone.
func InboxCleanup(ctx context.Context, s *state.State) error {
	fileName := s.EncName()
	tok, err := resolveListingToken(s)
	if err != nil {
		return err
	}
	listURL := fmt.Sprintf("https://%s:%s/files?inbox=true", s.Config.ProxyHost, s.Config.ProxyPort)
	s.Log.Info("verifying mapper removed file from inbox", "file", fileName, "url", listURL)

	client := httpx.New()
	attempts := s.Config.ExportRequestMaxRetries
	intervalMillis := int(s.Config.ExportRequestIntervalInSeconds * 1000)
	present := true
	for i := 1; i <= attempts; i++ {
		res, err := client.Do(ctx, "GET", listURL,
			httpx.WithProxyBearer(tok),
			httpx.WithBasicAuth(s.Config.CegaAuthUsername, s.Config.CegaAuthPassword))
		if err != nil {
			return err
		}
		if err := check.Equal(res.Status, 200, "inbox listing request failed"); err != nil {
			return err
		}
		var listing fileListing
		if err := json.Unmarshal(res.Body, &listing); err != nil {
			return fmt.Errorf("parsing inbox listing: %w", err)
		}
		present = false
		for _, f := range listing.Files {
			if f.FileName == fileName {
				present = true
				break
			}
		}
		if !present {
			s.Log.Info("inbox cleanup verified", "file", fileName, "attempt", i)
			break
		}
		s.Log.Info("file still present in inbox", "file", fileName, "attempt", i, "max", attempts)
		common.WaitForProcessing(intervalMillis)
	}

	return check.False(present, "mapper did not remove the uploaded file from the inbox: "+fileName)
}

// resolveListingToken returns a provided LS-AAI token, otherwise a minted visa.
func resolveListingToken(s *state.State) (string, error) {
	if s.Config.LSAAIToken == "" {
		return token.GenerateVisaToken(s.Config, "upload", "jwt.priv.pem")
	}
	return s.Config.LSAAIToken, nil
}
