package transfer

import (
	"context"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/proxy"
)

type downloadFunc func(ctx context.Context, name string) (io.ReadCloser, int64, error)

func (f downloadFunc) Download(ctx context.Context, name string) (io.ReadCloser, int64, error) {
	return f(ctx, name)
}

func serve(body string) downloadFunc {
	return func(context.Context, string) (io.ReadCloser, int64, error) {
		return io.NopCloser(strings.NewReader(body)), -1, nil
	}
}

// cancellingReader hands out its first read, then cancels the run as Ctrl-C
// would and fails the way a request with a cancelled context does.
type cancellingReader struct {
	first  string
	cancel context.CancelFunc
	read   bool
}

func (c *cancellingReader) Read(p []byte) (int, error) {
	if !c.read {
		c.read = true
		return copy(p, c.first), nil
	}
	c.cancel()
	return 0, context.Canceled
}

func TestDownloadLeavesNoPartialFile(t *testing.T) {
	tests := []struct {
		name string
		d    func(cancel context.CancelFunc) Downloader
	}{
		{
			name: "500",
			d: func(context.CancelFunc) Downloader {
				return downloadFunc(func(context.Context, string) (io.ReadCloser, int64, error) {
					return nil, 0, &proxy.APIError{Method: http.MethodGet, Path: "/stream/a.c4gh", Status: http.StatusInternalServerError}
				})
			},
		},
		{name: "truncated_body", d: func(context.CancelFunc) Downloader { return serve("01234") }},
		{name: "longer_body", d: func(context.CancelFunc) Downloader { return serve("0123456789ab") }},
		{
			name: "cancelled_context",
			d: func(cancel context.CancelFunc) Downloader {
				return downloadFunc(func(context.Context, string) (io.ReadCloser, int64, error) {
					return io.NopCloser(&cancellingReader{first: "0123", cancel: cancel}), -1, nil
				})
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dir := t.TempDir()
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			var rec recorder
			rep := rec.runner(4, 3).Download(ctx, tt.d(cancel), []plan.DownloadStep{{Name: "a.c4gh", Dir: dir, Action: plan.Fetch, Size: 10}})
			if res := rep.Results[0]; res.Outcome != Failed || res.Err == nil {
				t.Errorf("outcome = %v, err = %v", res.Outcome, res.Err)
			}
			assertEmptyDir(t, dir)
		})
	}
}

func TestDownload(t *testing.T) {
	dir := t.TempDir()
	steps := []plan.DownloadStep{
		{Name: "a.c4gh", Dir: dir, Action: plan.Fetch, Size: 10},
		{Name: "b.c4gh", Dir: dir, Action: plan.Skip, Reason: "already downloaded", Size: 3},
		{Name: "c.c4gh", Dir: dir, Action: plan.Reject, Reason: "not in the outbox"},
	}
	var rec recorder
	rep := rec.runner(4, 0).Download(context.Background(), serve("0123456789"), steps)
	want := []Outcome{Downloaded, Skipped, Rejected}
	for i, res := range rep.Results {
		if res.Outcome != want[i] {
			t.Errorf("%s: outcome %v (%v), want %v", res.Name, res.Outcome, res.Err, want[i])
		}
	}
	got, err := os.ReadFile(filepath.Join(dir, "a.c4gh"))
	if err != nil || string(got) != "0123456789" {
		t.Fatalf("a.c4gh = %q, %v", got, err)
	}
	entries, _ := os.ReadDir(dir)
	if len(entries) != 1 {
		t.Errorf("dir holds %d entries, want only a.c4gh", len(entries))
	}
	if rep.Done() != 2 || rep.Failed() != 1 {
		t.Errorf("Done = %d, Failed = %d", rep.Done(), rep.Failed())
	}
}

func TestDownloadRetriesFromScratch(t *testing.T) {
	dir := t.TempDir()
	calls := 0
	d := downloadFunc(func(context.Context, string) (io.ReadCloser, int64, error) {
		calls++
		if calls == 1 {
			return io.NopCloser(strings.NewReader("012")), -1, nil
		}
		return io.NopCloser(strings.NewReader("0123456789")), 10, nil
	})
	var rec recorder
	rep := rec.runner(4, 1).Download(context.Background(), d, []plan.DownloadStep{{Name: "a.c4gh", Dir: dir, Action: plan.Fetch, Size: 10}})
	if res := rep.Results[0]; res.Outcome != Downloaded || calls != 2 {
		t.Fatalf("outcome %v (%v), calls %d", res.Outcome, res.Err, calls)
	}
	if got, _ := os.ReadFile(filepath.Join(dir, "a.c4gh")); string(got) != "0123456789" {
		t.Errorf("a.c4gh = %q", got)
	}
}

func TestDownloadNotRetriedOn404(t *testing.T) {
	calls := 0
	d := downloadFunc(func(context.Context, string) (io.ReadCloser, int64, error) {
		calls++
		return nil, 0, &proxy.APIError{Status: http.StatusNotFound}
	})
	var rec recorder
	rep := rec.runner(4, 5).Download(context.Background(), d, []plan.DownloadStep{{Name: "a.c4gh", Dir: t.TempDir(), Action: plan.Fetch, Size: 10}})
	var apiErr *proxy.APIError
	if res := rep.Results[0]; !errors.As(res.Err, &apiErr) || calls != 1 {
		t.Errorf("err %v, calls %d", res.Err, calls)
	}
}

func assertEmptyDir(t *testing.T, dir string) {
	t.Helper()
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range entries {
		t.Errorf("left behind %s", e.Name())
	}
}
