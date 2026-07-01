package stages

import (
	"context"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/pg"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/check"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
)

// Finalize verifies the file reached status READY in the SDA database and
// records its archive path, asserting the stable id matches what we sent.
func Finalize(ctx context.Context, s *state.State) error {
	s.Log.Info("verifying state after finalize step")
	// The DB stores the anonymized submission path; SDA reconstructs the
	// physical p11-<user>/files/ path on access. See FEGA-Norway#820.
	inboxPath := fmt.Sprintf("/files/%s", s.EncName())

	archivePath, stableID, err := pg.VerifyFinalized(ctx, s.Config, inboxPath)
	if err != nil {
		return err
	}
	s.ArchivePath = archivePath

	if err := check.Equal(stableID, s.StableID, "DB stable_id must match the accession id we sent"); err != nil {
		return err
	}
	s.Log.Info("verification completed", "stableID", s.StableID, "archivePath", s.ArchivePath)
	return nil
}
