// Package pipeline orders the stages into the per-environment sequences and runs
// them. Each sequence is strictly sequential and aborts on the first failed
// stage, so a failure never cascades against stale state. The fixed inter-stage
// waits give the asynchronous SDA services time to settle, a candidate for
// replacement by bounded polling.
package pipeline

import (
	"context"
	"errors"
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
	for i, stage := range steps {
		s.Log.Stage(i+1, len(steps), stage.name)
		t0 := time.Now()
		if err := stage.fn(ctx, s); err != nil {
			s.Log.Fail(time.Since(t0), err)
			s.Log.Summary(false, i, len(steps), time.Since(start))
			return fmt.Errorf("stage %q failed: %w", stage.name, err)
		}
		s.Log.Pass(time.Since(t0))
		if stage.postWaitMs > 0 {
			s.Log.Wait(stage.postWaitMs)
			common.WaitForProcessing(stage.postWaitMs)
		}
	}
	s.Log.Summary(true, len(steps), len(steps), time.Since(start))
	return nil
}

func runRed(ctx context.Context, s *state.State, name string, steps []step) error {
	s.Log.Banner(name+" e2e", fmt.Sprintf("%d checks", len(steps)))
	start := time.Now()
	passed := 0
	var failures []error

	for i, stage := range steps {
		s.Log.Stage(i+1, len(steps), stage.name)
		t0 := time.Now()
		checkCtx, cancel := context.WithTimeout(ctx, 5*time.Minute)
		err := stage.fn(checkCtx, s)
		cancel()
		if err != nil {
			s.Log.Fail(time.Since(t0), err)
			failures = append(failures, fmt.Errorf("check %q failed: %w", stage.name, err))
			continue
		}
		s.Log.Pass(time.Since(t0))
		passed++
	}

	s.Log.Summary(len(failures) == 0, passed, len(steps), time.Since(start))
	return errors.Join(failures...)
}

// RunGreenLocal runs the FEGA pipeline against the mocked stack:
// ProxyWebpage -> C1 -> upload -> ingest -> accession -> finalize -> mapping -> inbox-cleanup ->
// release -> download.
func RunGreenLocal(ctx context.Context, s *state.State) error {
	return run(ctx, s, "FEGA (local) green pipeline", []step{
		{"ProxyWebpageVerification", stages.ProxyWebpage, 0},
		{"UploadViaLegaCmd", stages.UploadViaLegaCmd, 5000},
		{"Ingest", stages.Ingest, 5000},
		{"Accession", stages.Accession, 5000},
		{"Finalize", stages.Finalize, 0},
		{"Mapping", stages.Mapping, 1000},
		{"InboxCleanup", stages.InboxCleanup, 0},
		{"Release", stages.Release, 1000},
		{"Download", stages.Download, 0},
		{"LocalExport", stages.DownloadViaLocalFegaExportRequest, 0},
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

// RunLocalIngestCancelReingest runs the cancel path against the mocked stack: a file is
// finalized, cancelled, then ingested again from the inbox without re-uploading:
// upload -> ingest -> accession -> finalize -> cancel -> re-ingest -> accession ->
// finalize -> mapping -> inbox-cleanup -> release -> download.
func RunLocalIngestCancelReingest(ctx context.Context, s *state.State) error {
	return run(ctx, s, "FEGA (local) ingest-cancel", []step{
		{"UploadViaLegaCmd", stages.UploadViaLegaCmd, 5000},
		{"Ingest", stages.Ingest, 5000},
		{"Accession", stages.Accession, 5000},
		{"Finalize", stages.Finalize, 0},
		{"Cancel", stages.Cancel, 5000},
		{"ReIngest", stages.ReIngest, 5000},
		{"Accession", stages.Accession, 5000},
		{"Finalize", stages.Finalize, 0},
		{"Mapping", stages.Mapping, 1000},
		{"InboxCleanup", stages.InboxCleanup, 0},
		{"Release", stages.Release, 1000},
		{"Download", stages.Download, 0},
	})
}

// RunLocalRed runs independent adversarial checks and reports every failure.
//
// Unlike the ordered green pipelines, a failed red check does not stop the
// remaining checks. The suite passes only when every invalid request is
// correctly refused.
func RunLocalRed(ctx context.Context, s *state.State) error {
	return runRed(ctx, s, "FEGA (Local) red pipeline", []step{
		{"MissingBasicAuth", stages.MissingBasicAuth, 0},
		{"MissingBearerToken", stages.MissingBearerToken, 0},
		{"WrongSigningKey", stages.WrongSigningKey, 0},
	})
}
