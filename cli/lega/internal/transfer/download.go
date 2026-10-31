package transfer

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// Download runs every step against d, one file at a time, and returns one
// Result per step. A file is written to a temporary name in its directory
// and renamed only once all its bytes have arrived, so the final name never
// holds a partial file.
func (r Runner) Download(ctx context.Context, d Downloader, steps []plan.DownloadStep) Report {
	var rep Report
	for _, s := range steps {
		rep.Results = append(rep.Results, r.downloadStep(ctx, d, s))
	}
	return rep
}

func (r Runner) downloadStep(ctx context.Context, d Downloader, s plan.DownloadStep) Result {
	switch s.Action {
	case plan.Skip:
		r.emit(Event{Kind: FileSkipped, Name: s.Name, Message: s.Reason})
		return downloadResult(s, Skipped, nil)
	case plan.Reject:
		return downloadResult(s, Rejected, nil)
	case plan.Fetch:
		return r.reportDownload(s, r.downloadFile(ctx, d, s))
	default:
		return downloadResult(s, Failed, fmt.Errorf("%s: no download action", s.Name))
	}
}

func (r Runner) reportDownload(s plan.DownloadStep, err error) Result {
	if err != nil {
		r.emit(Event{Kind: FileFailed, Name: s.Name, Err: err})
		return downloadResult(s, Failed, err)
	}
	r.emit(Event{Kind: FileDone, Name: s.Name, Outcome: Downloaded})
	return downloadResult(s, Downloaded, nil)
}

func downloadResult(s plan.DownloadStep, o Outcome, err error) Result {
	res := Result{Name: s.Name, Path: filepath.Join(s.Dir, s.Name), Outcome: o, Size: s.Size, Err: err}
	if o == Skipped || o == Rejected {
		res.Reason = s.Reason
	}
	return res
}

// downloadFile fetches one file, starting over from the first byte on a
// retryable failure.
func (r Runner) downloadFile(ctx context.Context, d Downloader, s plan.DownloadStep) error {
	return r.Retry(ctx, func(ctx context.Context) error {
		if err := ctx.Err(); err != nil {
			return err
		}
		r.emit(Event{Kind: FileStarted, Name: s.Name, Size: s.Size})
		return r.fetch(ctx, d, s)
	})
}

func (r Runner) fetch(ctx context.Context, d Downloader, s plan.DownloadStep) error {
	body, _, err := d.Download(ctx, s.Name)
	if err != nil {
		return err
	}
	defer body.Close()
	tmp, err := os.CreateTemp(s.Dir, "."+s.Name+".*.part")
	if err != nil {
		return err
	}
	if err := r.fill(tmp, body, s); err != nil {
		tmp.Close()
		os.Remove(tmp.Name())
		return err
	}
	return commit(tmp, filepath.Join(s.Dir, s.Name))
}

// fill copies body into tmp and checks it got exactly the size the listing
// promised. A body cut short is an error even when the connection closed
// cleanly, since the proxy streams without declaring a length.
func (r Runner) fill(tmp io.Writer, body io.Reader, s plan.DownloadStep) error {
	counted := &progressWriter{w: tmp, emit: r.emit, name: s.Name}
	n, err := io.Copy(counted, io.LimitReader(body, s.Size+1))
	if err != nil {
		return fmt.Errorf("download %s: %w", s.Name, err)
	}
	if n != s.Size {
		return fmt.Errorf("download %s: got %d bytes, the outbox lists %d", s.Name, n, s.Size)
	}
	return nil
}

// commit closes tmp and moves it to its final name, removing it if either
// step fails.
func commit(tmp *os.File, final string) error {
	err := tmp.Close()
	if err == nil {
		err = os.Rename(tmp.Name(), final)
	}
	if err != nil {
		os.Remove(tmp.Name())
	}
	return err
}

// progressWriter reports every write as a Bytes event.
type progressWriter struct {
	w    io.Writer
	emit func(Event)
	name string
}

func (p *progressWriter) Write(b []byte) (int, error) {
	n, err := p.w.Write(b)
	p.emit(Event{Kind: Bytes, Name: p.name, N: int64(n)})
	return n, err
}
