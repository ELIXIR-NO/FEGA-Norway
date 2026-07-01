// Package stages holds one function per pipeline stage. Each operates on an
// explicit *state.State and returns an error, which aborts the pipeline, rather
// than panicking.
package stages

import (
	"context"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/amqp"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/common"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/constants"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
)

// Ingest publishes the ingestion message to CEGA and seeds the correlation id
// threaded through the rest of the pipeline.
func Ingest(_ context.Context, s *state.State) error {
	s.Log.Info("publishing ingestion message to CentralEGA")
	s.CorrelationID = common.NewUUID()
	// Anonymized path: just the filename under /files/. SDA rebuilds the
	// project-code path from the interceptor-mapped user. See FEGA-Norway#820.
	msg := fmt.Sprintf(constants.IngestMessage, s.Config.CegaAuthUsername, s.EncName())
	s.Log.JSON("ingest message", []byte(msg))
	return amqp.Publish(s.Config, s.CorrelationID, []byte(msg))
}

// Accession generates the file accession id and publishes the accession
// message.
func Accession(_ context.Context, s *state.State) error {
	s.Log.Info("publishing accession message on behalf of CEGA")
	s.StableID = "EGAF5" + common.RandomDigits(10) // shortcut; see Finalize
	msg := fmt.Sprintf(constants.AccessionMessage,
		s.Config.CegaAuthUsername, s.EncName(), s.StableID, s.RawSHA256, s.RawMD5)
	s.Log.JSON("accession message", []byte(msg))
	return amqp.Publish(s.Config, s.CorrelationID, []byte(msg))
}

// Mapping generates the dataset id and publishes the mapping message.
func Mapping(_ context.Context, s *state.State) error {
	s.Log.Info("mapping file to a dataset")
	s.DatasetID = "EGAD" + common.RandomDigits(11)
	msg := fmt.Sprintf(constants.MappingMessage, s.StableID, s.DatasetID)
	s.Log.JSON("mapping message", []byte(msg))
	if err := amqp.Publish(s.Config, s.CorrelationID, []byte(msg)); err != nil {
		return err
	}
	s.Log.Info("mapping file to dataset ID message sent successfully")
	return nil
}

// Release publishes the dataset release message.
func Release(_ context.Context, s *state.State) error {
	s.Log.Info("releasing the dataset")
	msg := fmt.Sprintf(constants.ReleaseMessage, s.DatasetID)
	s.Log.JSON("release message", []byte(msg))
	if err := amqp.Publish(s.Config, s.CorrelationID, []byte(msg)); err != nil {
		return err
	}
	s.Log.Info("dataset release message sent successfully")
	return nil
}
