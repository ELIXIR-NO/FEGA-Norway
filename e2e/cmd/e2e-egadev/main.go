// Command e2e-egadev runs the EGA_DEV pipeline against the live egadev
// environment (real services, real LS-AAI token). It runs directly on a host,
// or in the container where the entrypoint selects it via E2E_ENV=egadev.
package main

import (
	"context"
	"os"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/pipeline"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/report"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
)

func main() { os.Exit(run()) }

func run() (code int) {
	log := report.New(os.Stdout)
	cfg := config.Load(config.IntegrationEgaDev)
	if err := cfg.Validate(); err != nil {
		log.Error("invalid config", "err", err)
		return 1
	}

	st, err := state.SetupStaging(cfg, log)
	if err != nil {
		log.Error("setup failed", "err", err)
		return 1
	}
	defer func() {
		if err := st.Cleanup(); err != nil {
			log.Error("cleanup failed", "err", err)
			code = 1
		}
	}()

	if err := pipeline.RunStaging(context.Background(), st); err != nil {
		return 1
	}
	return 0
}
