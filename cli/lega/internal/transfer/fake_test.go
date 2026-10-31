package transfer

import (
	"context"
	"crypto/md5"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/proxy"
)

// failure makes one PutChunk call fail. merge says whether the backend
// still kept the chunk, as when only the answer is lost.
type failure struct {
	merge bool
	err   error
}

// fakeBackend holds one upload the way pyresumable does: chunks merge only
// in order, and a chunk number at or below the last merged one is refused.
type fakeBackend struct {
	data      []byte
	maxChunk  int64
	id        string
	merged    []int64
	ids       []string // the upload ID each PutChunk call carried
	puts      int
	failOn    map[int]failure
	finished  bool
	size      int64
	sha256    string
	notifyErr error
}

func (b *fakeBackend) PutChunk(_ context.Context, _, uploadID string, c plan.Chunk, data []byte) (string, error) {
	b.puts++
	b.ids = append(b.ids, uploadID)
	f, failing := b.failOn[b.puts]
	if failing && !f.merge {
		return "", f.err
	}
	if err := b.merge(uploadID, c, data); err != nil {
		return "", err
	}
	if failing {
		return "", f.err
	}
	return b.id, nil
}

func (b *fakeBackend) merge(uploadID string, c plan.Chunk, data []byte) error {
	if uploadID != b.id || c.Num != b.maxChunk+1 || c.Offset != int64(len(b.data)) {
		return &proxy.APIError{Method: http.MethodPatch, Status: http.StatusBadRequest, Body: "chunk_order_incorrect"}
	}
	if b.id == "" {
		b.id = "upload-1"
	}
	b.data = append(b.data, data...)
	b.maxChunk = c.Num
	b.merged = append(b.merged, c.Num)
	return nil
}

func (b *fakeBackend) Resumable(_ context.Context, name, _ string) (plan.Resumable, bool, error) {
	if b.id == "" {
		return plan.Resumable{}, false, nil
	}
	return plan.Resumable{ID: b.id, Name: name, NextOffset: int64(len(b.data)), MaxChunk: b.maxChunk}, true, nil
}

func (b *fakeBackend) Finish(_ context.Context, _, uploadID string, size int64, sha string) error {
	if uploadID != b.id {
		return fmt.Errorf("finish %q, backend has %q", uploadID, b.id)
	}
	b.finished, b.size, b.sha256 = true, size, sha
	return nil
}

// fakeTSD and fakeProxy are the two halves of a direct upload over the same
// backend state.
type fakeTSD struct{ b *fakeBackend }

func (t fakeTSD) PutChunk(ctx context.Context, name, uploadID string, c plan.Chunk, data []byte) (string, error) {
	return t.b.PutChunk(ctx, name, uploadID, c, data)
}

func (t fakeTSD) Finish(_ context.Context, _, uploadID string) error {
	if uploadID != t.b.id {
		return fmt.Errorf("finish %q, backend has %q", uploadID, t.b.id)
	}
	t.b.finished = true
	return nil
}

type fakeProxy struct{ b *fakeBackend }

func (p fakeProxy) Resumable(ctx context.Context, name, uploadID string) (plan.Resumable, bool, error) {
	return p.b.Resumable(ctx, name, uploadID)
}

func (p fakeProxy) Notify(_ context.Context, _ string, size int64, sha string) error {
	if p.b.notifyErr != nil {
		return p.b.notifyErr
	}
	p.b.size, p.b.sha256 = size, sha
	return nil
}

// backends runs a test against a proxy upload and a direct upload.
var backends = []struct {
	name     string
	uploader func(b *fakeBackend) Uploader
}{
	{name: "proxy", uploader: func(b *fakeBackend) Uploader { return b }},
	{name: "direct", uploader: func(b *fakeBackend) Uploader { return DirectUploader{TSD: fakeTSD{b}, Proxy: fakeProxy{b}} }},
}

// recorder collects the events and sleeps of a run.
type recorder struct {
	events []Event
	sleeps []time.Duration
}

func (r *recorder) runner(chunkSize int64, retries int) Runner {
	return Runner{
		ChunkSize: chunkSize,
		Retries:   retries,
		Progress:  func(e Event) { r.events = append(r.events, e) },
		Sleep: func(_ context.Context, d time.Duration) error {
			r.sleeps = append(r.sleeps, d)
			return nil
		},
	}
}

func (r *recorder) count(kind EventKind) int {
	n := 0
	for _, e := range r.events {
		if e.Kind == kind {
			n++
		}
	}
	return n
}

func writeFile(t *testing.T, name string, content []byte) plan.LocalFile {
	t.Helper()
	path := filepath.Join(t.TempDir(), name)
	if err := os.WriteFile(path, content, 0o600); err != nil {
		t.Fatal(err)
	}
	return plan.LocalFile{Path: path, Name: name, Size: int64(len(content)), Crypt4GH: true}
}

func sha256Hex(b []byte) string {
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:])
}

func md5Hex(b []byte) string {
	sum := md5.Sum(b)
	return hex.EncodeToString(sum[:])
}
