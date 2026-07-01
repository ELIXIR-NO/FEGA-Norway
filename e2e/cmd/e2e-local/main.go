// Command e2e-local runs the FEGA pipeline against the self-contained,
// fully-mocked docker-compose stack (the GitHub CI environment). Selected by
// E2E_ENV=local at the container entrypoint.
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

func run() int {
	log := report.New(os.Stdout)
	cfg := config.Load(config.IntegrationFEGA)
	if err := cfg.Validate(); err != nil {
		log.Error("invalid config", "err", err)
		return 1
	}

	st, err := state.SetupLocal(cfg, log)
	if err != nil {
		log.Error("setup failed", "err", err)
		return 1
	}
	defer func() {
		if err := st.Cleanup(); err != nil {
			log.Warn("cleanup failed", "err", err)
		}
	}()

	if err := pipeline.RunLocal(context.Background(), st); err != nil {
		return 1
	}
	return 0
}
