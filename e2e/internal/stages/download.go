package stages

import (
	"bytes"
	"context"
	"fmt"
	"strings"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/c4gh"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/common"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/constants"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/token"
)

// Download fetches the dataset metadata and the file (plain and Crypt4GH) from
// the DOA and verifies the checksums and metadata round-trip.
func Download(ctx context.Context, s *state.State) error {
	tok, err := token.GenerateVisaToken(s.Config, s.DatasetID, "jwt.priv.pem")
	if err != nil {
		return err
	}
	client := httpx.New()
	doa := func(path string) string {
		return fmt.Sprintf("https://%s:%s%s", s.Config.SdaDoaHost, s.Config.SdaDoaPort, path)
	}

	// 1) dataset listing
	res, err := client.Do(ctx, "GET", doa("/metadata/datasets"), httpx.WithBearer(tok))
	if err != nil {
		return err
	}
	if err := check.Equal(strings.TrimSpace(string(res.Body)),
		fmt.Sprintf("[%q]", s.DatasetID), "DOA dataset listing"); err != nil {
		s.Log.Check("dataset listing", false)
		return err
	}
	s.Log.Check("dataset listing", true)

	// 2) file metadata (lenient JSON match)
	expected, err := common.CompactJSON(strings.TrimSpace(fmt.Sprintf(constants.ExpectedDownloadMetadata,
		s.StableID, s.DatasetID, s.EncName(), s.ArchivePath, s.RawSHA256)))
	if err != nil {
		return err
	}
	metaRes, err := client.Do(ctx, "GET", doa("/metadata/datasets/"+s.DatasetID+"/files"), httpx.WithBearer(tok))
	if err != nil {
		return err
	}
	actual, err := common.CompactJSON(strings.TrimSpace(string(metaRes.Body)))
	if err != nil {
		return err
	}
	if err := check.JSONEqualLenient([]byte(expected), []byte(actual), "DOA file metadata"); err != nil {
		s.Log.Check("file metadata (lenient)", false)
		s.Log.JSON("expected metadata", []byte(expected))
		s.Log.JSON("actual metadata", []byte(actual))
		return err
	}
	s.Log.Check("file metadata (lenient)", true)

	// 3) plain file: checksum must match the original
	plainRes, err := client.Do(ctx, "GET", doa("/files/"+s.StableID), httpx.WithBearer(tok))
	if err != nil {
		return err
	}
	if plainRes.Status != 200 {
		return check.Failf("failed to fetch the file. Status: %d", plainRes.Status)
	}
	if err := check.Equal(common.Sha256HexBytes(plainRes.Body), s.RawSHA256, "plain download checksum"); err != nil {
		s.Log.Check("plain download checksum", false)
		return err
	}
	s.Log.Check("plain download checksum", true)

	// 4) Crypt4GH file: decrypt with a fresh recipient key, checksum must match
	recipient, err := c4gh.GenerateKeyPair()
	if err != nil {
		return err
	}
	pubArmored, err := c4gh.PublicKeyArmored(recipient.Public)
	if err != nil {
		return err
	}
	// The DOA strips the armor lines and all whitespace before base64-decoding
	// this header, so a single-line value decodes identically to the multi-line
	// PEM. net/http rejects header values containing newlines, so collapse it to
	// one line.
	pubHeader := strings.NewReplacer("\r", "", "\n", "").Replace(pubArmored)
	encRes, err := client.Do(ctx, "GET", doa("/files/"+s.StableID+"?destinationFormat=CRYPT4GH"),
		httpx.WithBearer(tok), httpx.WithHeader("Public-Key", pubHeader))
	if err != nil {
		return err
	}
	if encRes.Status != 200 {
		return check.Failf("failed to fetch the encrypted file. Status: %d", encRes.Status)
	}
	decrypted, err := c4gh.Decrypt(bytes.NewReader(encRes.Body), recipient.Private)
	if err != nil {
		return fmt.Errorf("decrypting downloaded Crypt4GH file: %w", err)
	}
	if err := check.Equal(common.Sha256HexBytes(decrypted), s.RawSHA256, "crypt4gh download checksum"); err != nil {
		s.Log.Check("crypt4gh download checksum", false)
		return err
	}
	s.Log.Check("crypt4gh download checksum", true)
	return nil
}
