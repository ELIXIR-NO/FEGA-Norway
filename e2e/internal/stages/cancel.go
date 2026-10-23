package stages

import (
	"context"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/amqp"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/constants"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
)

// Cancel publishes a cancel message for the file, reusing the CorrelationID SDA
// knows it by.
func Cancel(_ context.Context, s *state.State) error {
	s.Log.Info("publishing cancel message to CentralEGA")
	msg := fmt.Sprintf(constants.CancelMessage, s.Config.CegaAuthUsername, s.EncName())
	s.Log.JSON("cancel message", []byte(msg))
	return amqp.Publish(s.Config, s.CorrelationID, []byte(msg))
}
