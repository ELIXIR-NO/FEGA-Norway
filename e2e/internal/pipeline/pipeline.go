// Package pipeline orders the stages into the per-environment sequences and runs
// them. Each sequence is strictly sequential and aborts on the first failed
// stage, so a failure never cascades against stale state. The fixed inter-stage
// waits give the asynchronous SDA services time to settle, a candidate for
// replacement by bounded polling.
package pipeline

import (
	"context"
	"fmt"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/common"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/stages"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/state"
)

type step struct {
	name       string
	fn         func(context.Context, *state.State) error
	postWaitMs int
}

func run(ctx context.Context, s *state.State, name string, steps []step) error {
	s.Log.Banner(name+" e2e", fmt.Sprintf("%d stages", len(steps)))
	start := time.Now()
	for i, st := range steps {
		s.Log.Stage(i+1, len(steps), st.name)
		t0 := time.Now()
		if err := st.fn(ctx, s); err != nil {
			s.Log.Fail(time.Since(t0), err)
			s.Log.Summary(false, i, len(steps), time.Since(start))
			return fmt.Errorf("stage %q failed: %w", st.name, err)
		}
		s.Log.Pass(time.Since(t0))
		if st.postWaitMs > 0 {
			s.Log.Wait(st.postWaitMs)
			common.WaitForProcessing(st.postWaitMs)
		}
	}
	s.Log.Summary(true, len(steps), len(steps), time.Since(start))
	return nil
}

// RunLocal runs the FEGA pipeline against the mocked stack:
// C1 -> upload -> ingest -> accession -> finalize -> mapping -> inbox-cleanup ->
// release -> download.
func RunLocal(ctx context.Context, s *state.State) error {
	return run(ctx, s, "FEGA (local)", []step{
		{"C1JwtSignatureVerification", stages.C1JwtSignatureVerification, 0},
		{"UploadViaLegaCmd", stages.UploadViaLegaCmd, 5000},
		{"Ingest", stages.Ingest, 5000},
		{"Accession", stages.Accession, 5000},
		{"Finalize", stages.Finalize, 0},
		{"Mapping", stages.Mapping, 1000},
		{"InboxCleanup", stages.InboxCleanup, 0},
		{"Release", stages.Release, 1000},
		{"Download", stages.Download, 0},
	})
}

// RunStaging runs the EGA_DEV pipeline against the live egadev environment:
// upload(proxy) -> ingest -> accession -> mapping -> release ->
// download(export-request). It has no C1, finalize or inbox-cleanup stage.
func RunStaging(ctx context.Context, s *state.State) error {
	return run(ctx, s, "EGA_DEV (staging)", []step{
		{"UploadThroughProxy", stages.UploadThroughProxy, 5000},
		{"Ingest", stages.Ingest, 5000},
		{"Accession", stages.Accession, 5000},
		{"Mapping", stages.Mapping, 1000},
		{"Release", stages.Release, 1000},
		{"DownloadViaFegaExportRequest", stages.DownloadViaFegaExportRequest, 0},
	})
}
