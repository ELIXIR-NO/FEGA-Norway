package cli

import (
	"context"
	"errors"
	"fmt"
	"io"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/proxy"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/transfer"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/ui"
)

// maxRetries bounds --retries: TSD's handling of a resent chunk is not
// confirmed, so retrying stays opt-in and short.
const maxRetries = 5

// loadConfig reads what need asks for and warns once about settings that
// weaken the connection.
func (e *env) loadConfig(need config.Need) (config.Config, error) {
	cfg, err := config.Load(e.getenv, need)
	if err != nil {
		return config.Config{}, err
	}
	e.warnConfig(cfg)
	return cfg, nil
}

// warnConfig names variables, never their values, since a URL can carry
// userinfo.
func (e *env) warnConfig(cfg config.Config) {
	for _, name := range cfg.Insecure {
		ui.Warnf(e.stderr, "%s is plain http; your token and credentials travel in clear", name)
	}
	if cfg.TLSSkipVerify {
		ui.Warnf(e.stderr, "LEGA_COMMANDER_TLS_SKIP_VERIFY is true, so TLS certificates are not checked; use it only against a test stack")
	}
}

func (e *env) proxyClient(cfg config.Config) *proxy.Client {
	return proxy.NewClient(cfg.LocalEGAInstanceURL, cfg.ElixirAAIToken, cfg.CentralEGAUsername, cfg.CentralEGAPassword, cfg.TLSSkipVerify)
}

func (e *env) runner(cfg config.Config, retries int) transfer.Runner {
	return transfer.Runner{ChunkSize: cfg.ChunkBytes(), Retries: retries, Progress: e.progress()}
}

// progress renders a run's events on stderr: a redrawn bar on a terminal,
// a line per file start and end anywhere else.
func (e *env) progress() func(transfer.Event) {
	p := ui.NewProgress(e.stderr, e.progressTTY)
	return func(ev transfer.Event) { showProgress(p, e.stderr, ev) }
}

func showProgress(p *ui.Progress, w io.Writer, ev transfer.Event) {
	switch ev.Kind {
	case transfer.FileStarted:
		p.Start(ev.Name, ev.Size, ev.Done)
	case transfer.Bytes:
		p.Add(ev.N)
	case transfer.FileDone:
		p.End(ev.Outcome.String())
	case transfer.FileFailed:
		p.End(transfer.Failed.String())
	case transfer.Warning:
		ui.Warnf(w, "%s: %s", ev.Name, ev.Message)
	}
}

func (e *env) renderBatch(b ui.Batch) error {
	if e.json {
		return ui.BatchJSON(e.stdout, b)
	}
	return e.styles.Batch(e.stdout, b)
}

func (e *env) renderJSON(v any) error {
	return ui.JSON(e.stdout, v)
}

// removeEach removes every name, going on past a failure, and reports one
// line per name.
func (e *env) removeEach(names []string, remove func(context.Context, string) error) error {
	var b ui.Batch
	var failures []error
	for _, name := range names {
		if err := remove(e.ctx, name); err != nil {
			failures = append(failures, err)
			b.Lines = append(b.Lines, ui.Line{Outcome: "failed", Name: name, Detail: err.Error()})
			continue
		}
		b.Lines = append(b.Lines, ui.Line{Outcome: "removed", Name: name})
	}
	b.Tallies = []ui.Tally{{Word: "removed", N: len(names) - len(failures)}, {Word: "failed", N: len(failures)}}
	if err := e.renderBatch(b); err != nil {
		return err
	}
	return newBatchError(len(names), len(names)-len(failures), failures)
}

// batchError reports a batch in which some files failed. With one file it
// is that file's error; with more it counts them, since each failure is
// already on its own summary line.
type batchError struct {
	total, done int
	failures    []error
}

func newBatchError(total, done int, failures []error) error {
	if len(failures) == 0 {
		return nil
	}
	return batchError{total: total, done: done, failures: failures}
}

func (b batchError) Error() string {
	if b.total == 1 {
		return b.failures[0].Error()
	}
	return fmt.Sprintf("%d of %d failed", len(b.failures), b.total)
}

// Unwrap exposes every failure to errors.Is and errors.As.
func (b batchError) Unwrap() []error { return b.failures }

func (b batchError) partial() bool { return b.done > 0 }

// reportError turns the failed files of a report into one batchError. A
// rejected file has no error of its own, so its reason stands in.
func reportError(rep transfer.Report) error {
	var failures []error
	for _, res := range rep.Results {
		switch {
		case res.Err != nil:
			failures = append(failures, res.Err)
		case res.Outcome == transfer.Rejected:
			failures = append(failures, fmt.Errorf("%s: %s", res.Name, res.Reason))
		}
	}
	return newBatchError(len(rep.Results), rep.Done(), failures)
}

// interrupted replaces a run's errors with what to do next once the user
// pressed Ctrl-C.
func interrupted(ctx context.Context, err error, next string) error {
	if ctx.Err() == nil || err == nil {
		return err
	}
	return errors.New("interrupted; " + next)
}

func validateRetries(n int) error {
	if n < 0 || n > maxRetries {
		return usageError{msg: fmt.Sprintf("--retries must be from 0 to %d, got %d", maxRetries, n)}
	}
	return nil
}

func noArguments(command string, args []string) error {
	if len(args) > 0 {
		return usageError{msg: fmt.Sprintf("%s takes no arguments, got %q", command, args[0])}
	}
	return nil
}
