package tsd

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

func TestStalledBodyCancels(t *testing.T) {
	tests := []struct {
		name        string
		handler     func(w http.ResponseWriter, r *http.Request, done <-chan struct{})
		size        int
		wantStalled bool
	}{
		{
			name:        "chunk the server stops reading",
			handler:     func(_ http.ResponseWriter, _ *http.Request, done <-chan struct{}) { <-done },
			size:        64 << 20,
			wantStalled: true,
		},
		{
			name: "long wait for the answer after the body is sent",
			handler: func(w http.ResponseWriter, r *http.Request, _ <-chan struct{}) {
				_, _ = io.Copy(io.Discard, r.Body)
				time.Sleep(400 * time.Millisecond)
				_, _ = io.WriteString(w, `{"id":"t-1"}`)
			},
			size: 4,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			done := make(chan struct{})
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { tt.handler(w, r, done) }))
			t.Cleanup(srv.Close)
			t.Cleanup(func() { close(done) })
			base, err := url.Parse(srv.URL)
			if err != nil {
				t.Fatal(err)
			}
			c := testClient(base)
			c.stallTimeout = 100 * time.Millisecond
			_, err = c.PutChunk(context.Background(), "a.c4gh", "", plan.Chunk{Num: 1, Len: int64(tt.size)}, make([]byte, tt.size))
			if stalled := errors.Is(err, httpx.ErrStalled); stalled != tt.wantStalled {
				t.Fatalf("err = %v, want stalled %v", err, tt.wantStalled)
			}
		})
	}
}
