package transfer

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io/fs"
	"net/http"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/proxy"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/tsd"
)

var content = []byte("0123456789")

func TestUploadResume(t *testing.T) {
	tests := []struct {
		name        string
		held        []byte // what the backend already has
		maxChunk    int64
		from        plan.Resumable
		wantOutcome Outcome
		wantPuts    int
		wantWarning bool
	}{
		{
			name:        "whole_file_sha256",
			held:        content[:4],
			maxChunk:    1,
			from:        plan.Resumable{PreviousOffset: 0, NextOffset: 4, MaxChunk: 1, LastChunkMD5: md5Hex(content[:4])},
			wantOutcome: Resumed,
			wantPuts:    2,
		},
		{
			name:        "all_bytes_already_sent",
			held:        content,
			maxChunk:    3,
			from:        plan.Resumable{PreviousOffset: 8, NextOffset: 10, MaxChunk: 3, LastChunkMD5: strings.ToUpper(md5Hex(content[8:]))},
			wantOutcome: Resumed,
		},
		{
			name:        "changed_file_refused",
			held:        []byte("abcd"),
			maxChunk:    1,
			from:        plan.Resumable{PreviousOffset: 0, NextOffset: 4, MaxChunk: 1, LastChunkMD5: md5Hex([]byte("abcd"))},
			wantOutcome: Failed,
		},
		{
			name:        "no_checksum_warns_and_resumes",
			held:        content[:4],
			maxChunk:    1,
			from:        plan.Resumable{NextOffset: 4, MaxChunk: 1},
			wantOutcome: Resumed,
			wantPuts:    2,
			wantWarning: true,
		},
	}
	for _, be := range backends {
		for _, tt := range tests {
			t.Run(be.name+"/"+tt.name, func(t *testing.T) {
				local := writeFile(t, "a.c4gh", content)
				b := &fakeBackend{data: bytes.Clone(tt.held), maxChunk: tt.maxChunk, id: "upload-7"}
				from := tt.from
				from.ID, from.Name = "upload-7", local.Name
				var rec recorder
				rep := rec.runner(4, 0).Upload(context.Background(), be.uploader(b), []plan.Step{{File: local, Action: plan.Resume, From: &from}})

				res := rep.Results[0]
				if res.Outcome != tt.wantOutcome {
					t.Fatalf("outcome = %v (%v), want %v", res.Outcome, res.Err, tt.wantOutcome)
				}
				if b.puts != tt.wantPuts {
					t.Errorf("PutChunk calls = %d, want %d", b.puts, tt.wantPuts)
				}
				if got := rec.count(Warning) > 0; got != tt.wantWarning {
					t.Errorf("warning = %v, want %v", got, tt.wantWarning)
				}
				if tt.wantOutcome == Failed {
					if b.finished || !strings.Contains(res.Err.Error(), "lega resumables rm upload-7") {
						t.Errorf("finished = %v, err = %v", b.finished, res.Err)
					}
					return
				}
				if !b.finished || b.size != 10 || b.sha256 != sha256Hex(content) {
					t.Errorf("finish: called %v, size %d, sha256 %s, want the whole file's %s", b.finished, b.size, b.sha256, sha256Hex(content))
				}
				if !bytes.Equal(b.data, content) || res.From != tt.from.NextOffset {
					t.Errorf("backend holds %q, From = %d", b.data, res.From)
				}
			})
		}
	}
}

func TestUpload(t *testing.T) {
	tests := []struct {
		name       string
		chunkSize  int64
		wantMerged []int64
		wantIDs    []string
	}{
		{name: "chunks with a remainder", chunkSize: 4, wantMerged: []int64{1, 2, 3}, wantIDs: []string{"", "upload-1", "upload-1"}},
		{name: "exact multiple", chunkSize: 5, wantMerged: []int64{1, 2}, wantIDs: []string{"", "upload-1"}},
		{name: "one chunk", chunkSize: 64, wantMerged: []int64{1}, wantIDs: []string{""}},
	}
	for _, be := range backends {
		for _, tt := range tests {
			t.Run(be.name+"/"+tt.name, func(t *testing.T) {
				local := writeFile(t, "a.c4gh", content)
				b := &fakeBackend{}
				var rec recorder
				rep := rec.runner(tt.chunkSize, 0).Upload(context.Background(), be.uploader(b), []plan.Step{{File: local, Action: plan.Upload}})
				if res := rep.Results[0]; res.Outcome != Uploaded {
					t.Fatalf("outcome = %v (%v)", res.Outcome, res.Err)
				}
				if !reflect.DeepEqual(b.merged, tt.wantMerged) || !bytes.Equal(b.data, content) {
					t.Errorf("merged %v holding %q", b.merged, b.data)
				}
				if !reflect.DeepEqual(b.ids, tt.wantIDs) {
					t.Errorf("upload IDs sent = %q, want %q", b.ids, tt.wantIDs)
				}
				if !b.finished || b.size != int64(len(content)) || b.sha256 != sha256Hex(content) {
					t.Errorf("finish: called %v, size %d, sha256 %s", b.finished, b.size, b.sha256)
				}
				if rec.count(FileStarted) != 1 || rec.count(Bytes) != len(tt.wantMerged) || rec.count(FileDone) != 1 {
					t.Errorf("events = %+v", rec.events)
				}
			})
		}
	}
}

func TestUploadReport(t *testing.T) {
	ok := writeFile(t, "ok.c4gh", content)
	broken := writeFile(t, "broken.c4gh", content)
	steps := []plan.Step{
		{File: ok, Action: plan.Upload},
		{File: plan.LocalFile{Path: "in/inbox.c4gh", Name: "inbox.c4gh"}, Action: plan.Skip, Reason: "already in the inbox"},
		{File: plan.LocalFile{Path: "notes.txt", Name: "notes.txt"}, Action: plan.Reject, Reason: "not a Crypt4GH file"},
		{File: broken, Action: plan.Upload},
		{File: plan.LocalFile{Path: "gone.c4gh", Name: "gone.c4gh", Size: 3}, Action: plan.Upload},
	}
	up := &perFile{backends: map[string]*fakeBackend{
		"ok.c4gh":     {},
		"broken.c4gh": {failOn: map[int]failure{1: {err: &proxy.APIError{Status: http.StatusInternalServerError}}}},
	}}
	var rec recorder
	rep := rec.runner(4, 0).Upload(context.Background(), up, steps)

	var got []Outcome
	for _, r := range rep.Results {
		got = append(got, r.Outcome)
	}
	want := []Outcome{Uploaded, Skipped, Rejected, Failed, Failed}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("outcomes = %v, want %v", got, want)
	}
	if rep.Done() != 2 || rep.Failed() != 3 {
		t.Errorf("Done = %d, Failed = %d", rep.Done(), rep.Failed())
	}
	if rep.Results[1].Reason != "already in the inbox" || rep.Results[2].Reason != "not a Crypt4GH file" {
		t.Errorf("reasons = %q, %q", rep.Results[1].Reason, rep.Results[2].Reason)
	}
	if rec.count(FileSkipped) != 1 || rec.count(FileFailed) != 2 {
		t.Errorf("events = %+v", rec.events)
	}
}

func TestUploadCancelled(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	local := writeFile(t, "a.c4gh", content)
	b := &fakeBackend{}
	var rec recorder
	rep := rec.runner(4, 0).Upload(ctx, b, []plan.Step{{File: local, Action: plan.Upload}})
	if res := rep.Results[0]; res.Outcome != Failed || !errors.Is(res.Err, context.Canceled) || b.puts != 0 {
		t.Errorf("outcome %v, err %v, puts %d", res.Outcome, res.Err, b.puts)
	}
}

// perFile routes each file to its own backend.
type perFile struct{ backends map[string]*fakeBackend }

func (p *perFile) PutChunk(ctx context.Context, name, id string, c plan.Chunk, data []byte) (string, error) {
	return p.backends[name].PutChunk(ctx, name, id, c, data)
}

func (p *perFile) Finish(ctx context.Context, name, id string, size int64, sha string) error {
	return p.backends[name].Finish(ctx, name, id, size, sha)
}

func (p *perFile) Resumable(ctx context.Context, name, id string) (plan.Resumable, bool, error) {
	return p.backends[name].Resumable(ctx, name, id)
}

func TestUploadRetry(t *testing.T) {
	unavailable := &proxy.APIError{Status: http.StatusServiceUnavailable}
	tests := []struct {
		name        string
		retries     int
		failOn      map[int]failure
		wantOutcome Outcome
		wantPuts    int
		wantSleeps  int
		wantIDs     []string
	}{
		{
			name:        "chunk_already_merged_continues",
			retries:     2,
			failOn:      map[int]failure{2: {merge: true, err: unavailable}},
			wantOutcome: Uploaded,
			wantPuts:    3,
			wantSleeps:  1,
		},
		{
			name:        "rolled_back_resends",
			retries:     2,
			failOn:      map[int]failure{2: {err: unavailable}},
			wantOutcome: Uploaded,
			wantPuts:    4,
			wantSleeps:  1,
		},
		{
			name:        "chunk1_orphan_adopted",
			retries:     1,
			failOn:      map[int]failure{1: {merge: true, err: errors.New("connection reset by peer")}},
			wantOutcome: Uploaded,
			wantPuts:    3,
			wantSleeps:  1,
			wantIDs:     []string{"", "upload-1", "upload-1"},
		},
		{
			name:        "chunk1_lost_resends",
			retries:     1,
			failOn:      map[int]failure{1: {err: &proxy.APIError{Status: http.StatusBadGateway}}},
			wantOutcome: Uploaded,
			wantPuts:    4,
			wantSleeps:  1,
			wantIDs:     []string{"", "", "upload-1", "upload-1"},
		},
		{
			name:        "500_not_retried",
			retries:     5,
			failOn:      map[int]failure{2: {err: &proxy.APIError{Status: http.StatusInternalServerError}}},
			wantOutcome: Failed,
			wantPuts:    2,
		},
		{
			name:        "retries_0_never_retries",
			retries:     0,
			failOn:      map[int]failure{2: {merge: true, err: unavailable}},
			wantOutcome: Failed,
			wantPuts:    2,
		},
		{
			name:        "retries_run_out",
			retries:     2,
			failOn:      map[int]failure{2: {err: unavailable}, 3: {err: unavailable}, 4: {err: unavailable}},
			wantOutcome: Failed,
			wantPuts:    4,
			wantSleeps:  2,
		},
	}
	for _, be := range backends {
		for _, tt := range tests {
			t.Run(be.name+"/"+tt.name, func(t *testing.T) {
				local := writeFile(t, "a.c4gh", content)
				b := &fakeBackend{failOn: tt.failOn}
				var rec recorder
				rep := rec.runner(4, tt.retries).Upload(context.Background(), be.uploader(b), []plan.Step{{File: local, Action: plan.Upload}})

				res := rep.Results[0]
				if res.Outcome != tt.wantOutcome {
					t.Fatalf("outcome = %v (%v), want %v", res.Outcome, res.Err, tt.wantOutcome)
				}
				if b.puts != tt.wantPuts || len(rec.sleeps) != tt.wantSleeps {
					t.Errorf("PutChunk calls = %d, sleeps = %d, want %d and %d", b.puts, len(rec.sleeps), tt.wantPuts, tt.wantSleeps)
				}
				if tt.wantIDs != nil && !reflect.DeepEqual(b.ids, tt.wantIDs) {
					t.Errorf("upload IDs sent = %q, want %q", b.ids, tt.wantIDs)
				}
				if tt.wantOutcome == Uploaded && (b.sha256 != sha256Hex(content) || !reflect.DeepEqual(b.merged, []int64{1, 2, 3})) {
					t.Errorf("merged %v, sha256 %s", b.merged, b.sha256)
				}
			})
		}
	}
}

func TestUploadChangedSize(t *testing.T) {
	local := writeFile(t, "a.c4gh", content)
	local.Size = 12
	b := &fakeBackend{}
	var rec recorder
	rep := rec.runner(4, 0).Upload(context.Background(), b, []plan.Step{{File: local, Action: plan.Upload}})
	if res := rep.Results[0]; res.Outcome != Failed || !strings.Contains(res.Err.Error(), "changed") || b.puts != 0 {
		t.Errorf("outcome %v, err %v, puts %d", res.Outcome, res.Err, b.puts)
	}
}

func TestDirectNotNotified(t *testing.T) {
	local := writeFile(t, "a.c4gh", content)
	b := &fakeBackend{notifyErr: &proxy.APIError{Status: http.StatusBadGateway}}
	var rec recorder
	rep := rec.runner(4, 0).Upload(context.Background(), DirectUploader{TSD: fakeTSD{b}, Proxy: fakeProxy{b}}, []plan.Step{{File: local, Action: plan.Upload}})
	var nn *NotNotifiedError
	res := rep.Results[0]
	if res.Outcome != Failed || !errors.As(res.Err, &nn) || !b.finished {
		t.Fatalf("outcome %v, err %v, TSD finished %v", res.Outcome, res.Err, b.finished)
	}
	if !strings.Contains(res.Err.Error(), "--force sends it again") {
		t.Errorf("err = %v", res.Err)
	}
}

func TestFateOf(t *testing.T) {
	c := plan.Chunk{Num: 3, Offset: 8, Len: 4}
	tests := []struct {
		name string
		r    plan.Resumable
		ok   bool
		want chunkFate
	}{
		{name: "merged", r: plan.Resumable{MaxChunk: 3}, ok: true, want: chunkMerged},
		{name: "rolled back", r: plan.Resumable{MaxChunk: 2}, ok: true, want: chunkRolledBack},
		{name: "behind", r: plan.Resumable{MaxChunk: 1}, ok: true, want: chunkLost},
		{name: "ahead", r: plan.Resumable{MaxChunk: 4}, ok: true, want: chunkLost},
		{name: "gone", ok: false, want: chunkLost},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := fateOf(tt.r, tt.ok, c); got != tt.want {
				t.Errorf("fateOf = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestRetryable(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want bool
	}{
		{name: "transport", err: errors.New("connection refused"), want: true},
		{name: "429", err: &proxy.APIError{Status: http.StatusTooManyRequests}, want: true},
		{name: "502", err: &proxy.APIError{Status: http.StatusBadGateway}, want: true},
		{name: "503", err: &proxy.APIError{Status: http.StatusServiceUnavailable}, want: true},
		{name: "504", err: &proxy.APIError{Status: http.StatusGatewayTimeout}, want: true},
		{name: "500", err: &proxy.APIError{Status: http.StatusInternalServerError}},
		{name: "400", err: &proxy.APIError{Status: http.StatusBadRequest}},
		{name: "403", err: &proxy.APIError{Status: http.StatusForbidden}},
		{name: "TSD 503", err: &tsd.APIError{Status: http.StatusServiceUnavailable}, want: true},
		{name: "TSD 400", err: &tsd.APIError{Status: http.StatusBadRequest}},
		{name: "stall", err: fmt.Errorf("PATCH /stream/a.c4gh: %w", httpx.ErrStalled), want: true},
		{name: "wrapped 502", err: fmt.Errorf("list the outbox: %w", &proxy.APIError{Status: http.StatusBadGateway}), want: true},
		{name: "local file", err: &fs.PathError{Op: "open", Path: "out/.a.c4gh.1.part", Err: fs.ErrPermission}},
		{name: "cancelled", err: context.Canceled},
		{name: "deadline", err: context.DeadlineExceeded},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := retryable(tt.err); got != tt.want {
				t.Errorf("retryable = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestBackoff(t *testing.T) {
	low := func(int64) int64 { return 0 }
	high := func(n int64) int64 { return n - 1 }
	tests := []struct {
		failures  int
		low, high time.Duration
	}{
		{1, 500 * time.Millisecond, time.Second},
		{2, time.Second, 2 * time.Second},
		{5, 8 * time.Second, 16 * time.Second},
		{6, 15 * time.Second, 30 * time.Second},
		{40, 15 * time.Second, 30 * time.Second},
	}
	for _, tt := range tests {
		if got := backoff(tt.failures, low); got != tt.low {
			t.Errorf("backoff(%d) low = %v, want %v", tt.failures, got, tt.low)
		}
		if got := backoff(tt.failures, high); got >= tt.high || got < tt.high-time.Nanosecond {
			t.Errorf("backoff(%d) high = %v, want just under %v", tt.failures, got, tt.high)
		}
	}
}

func TestRetry(t *testing.T) {
	var rec recorder
	r := rec.runner(4, 2)
	calls := 0
	err := r.Retry(context.Background(), func(context.Context) error {
		calls++
		if calls < 3 {
			return &proxy.APIError{Status: http.StatusServiceUnavailable}
		}
		return nil
	})
	if err != nil || calls != 3 || len(rec.sleeps) != 2 {
		t.Errorf("err %v, calls %d, sleeps %d", err, calls, len(rec.sleeps))
	}
}
