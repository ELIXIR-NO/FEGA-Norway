package proxy

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

// stallingServer runs handler with a channel that closes when the test
// ends, so a handler that stalls on purpose still returns.
func stallingServer(t *testing.T, handler func(w http.ResponseWriter, r *http.Request, done <-chan struct{})) *Client {
	t.Helper()
	done := make(chan struct{})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		handler(w, r, done)
	}))
	t.Cleanup(srv.Close)
	t.Cleanup(func() { close(done) })
	base, err := url.Parse(srv.URL)
	if err != nil {
		t.Fatal(err)
	}
	c := NewClient(base, testToken, testUser, testPassword, false)
	c.stallTimeout = 100 * time.Millisecond
	return c
}

func TestStalledBodyCancels(t *testing.T) {
	tests := []struct {
		name        string
		handler     func(w http.ResponseWriter, r *http.Request, done <-chan struct{})
		call        func(c *Client) error
		wantStalled bool
	}{
		{
			name: "download body that stops",
			handler: func(w http.ResponseWriter, _ *http.Request, done <-chan struct{}) {
				_, _ = io.WriteString(w, "first bytes")
				w.(http.Flusher).Flush()
				<-done
			},
			call:        download,
			wantStalled: true,
		},
		{
			name: "chunk the server stops reading",
			handler: func(_ http.ResponseWriter, _ *http.Request, done <-chan struct{}) {
				<-done
			},
			call: func(c *Client) error {
				_, err := c.PutChunk(context.Background(), "a.c4gh", "", plan.Chunk{Num: 1, Len: 64 << 20}, make([]byte, 64<<20))
				return err
			},
			wantStalled: true,
		},
		{
			name: "slow download that keeps moving",
			handler: func(w http.ResponseWriter, _ *http.Request, _ <-chan struct{}) {
				for range 10 {
					_, _ = io.WriteString(w, "x")
					w.(http.Flusher).Flush()
					time.Sleep(30 * time.Millisecond)
				}
			},
			call: download,
		},
		{
			name: "long wait for the answer after the body is sent",
			handler: func(w http.ResponseWriter, r *http.Request, _ <-chan struct{}) {
				_, _ = io.Copy(io.Discard, r.Body)
				time.Sleep(400 * time.Millisecond)
				_, _ = io.WriteString(w, `{"id":"u-1"}`)
			},
			call: func(c *Client) error {
				_, err := c.PutChunk(context.Background(), "a.c4gh", "", plan.Chunk{Num: 1, Len: 4}, []byte("data"))
				return err
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := stallingServer(t, tt.handler)
			err := tt.call(c)
			if stalled := errors.Is(err, httpx.ErrStalled); stalled != tt.wantStalled {
				t.Fatalf("err = %v, want stalled %v", err, tt.wantStalled)
			}
			if tt.wantStalled && errors.Is(err, context.Canceled) {
				t.Errorf("a stall reads as a cancelled context: %v", err)
			}
		})
	}
}

func download(c *Client) error {
	body, _, err := c.Download(context.Background(), "a.c4gh")
	if err != nil {
		return err
	}
	defer body.Close()
	_, err = io.Copy(io.Discard, body)
	return err
}
