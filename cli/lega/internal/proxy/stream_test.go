package proxy

import (
	"context"
	"io"
	"net/http"
	"net/url"
	"strings"
	"testing"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

var testChunk = plan.Chunk{Num: 1, Offset: 0, Len: 4}

func TestPutChunkSendsMD5(t *testing.T) {
	tests := []struct {
		name, uploadID string
		chunk          plan.Chunk
		data           string
		answer         string
		wantQuery      url.Values
		wantID         string
		wantErr        bool
	}{
		{
			name: "first chunk", chunk: plan.Chunk{Num: 1, Len: 5}, data: "hello", answer: `{"id":"u-1","fileName":"a.c4gh","maxChunk":"1"}`,
			wantQuery: url.Values{"chunk": {"1"}, "md5": {"5d41402abc4b2a76b9719d911017c592"}}, wantID: "u-1",
		},
		{
			name: "later chunk carries the upload ID", uploadID: "u-1", chunk: plan.Chunk{Num: 4, Offset: 15, Len: 5}, data: "world", answer: `{"id":"u-1"}`,
			wantQuery: url.Values{"chunk": {"4"}, "md5": {"7d793037a0760186574b0282f2f435e7"}, "uploadId": {"u-1"}}, wantID: "u-1",
		},
		{
			name: "answer without an ID", chunk: plan.Chunk{Num: 1, Len: 5}, data: "hello", answer: `{}`,
			wantQuery: url.Values{"chunk": {"1"}, "md5": {"5d41402abc4b2a76b9719d911017c592"}}, wantErr: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c, log := fakeProxy(t, answer(http.StatusOK, tt.answer))
			id, err := c.PutChunk(context.Background(), "a.c4gh", tt.uploadID, tt.chunk, []byte(tt.data))
			if (err != nil) != tt.wantErr {
				t.Fatalf("err = %v, wantErr %v", err, tt.wantErr)
			}
			if id != tt.wantID {
				t.Errorf("id = %q, want %q", id, tt.wantID)
			}
			req := log.all()[0]
			if req.method != http.MethodPatch || req.path != "/stream/a.c4gh" {
				t.Errorf("sent %s %s", req.method, req.path)
			}
			if req.rawQuery != tt.wantQuery.Encode() {
				t.Errorf("query = %s, want %s", req.rawQuery, tt.wantQuery.Encode())
			}
			if string(req.body) != tt.data {
				t.Errorf("body = %q, want %q", req.body, tt.data)
			}
			if user, pass, ok := basicAuth(req); !ok || user != testUser || pass != testPassword {
				t.Error("chunk carries no CEGA credentials")
			}
		})
	}
}

func TestFinishParams(t *testing.T) {
	const sum = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
	tests := []struct {
		name       string
		call       func(*Client) error
		wantMethod string
		wantPath   string
		wantQuery  url.Values
	}{
		{
			name:       "finish",
			call:       func(c *Client) error { return c.Finish(context.Background(), "a.c4gh", "u-1", 1234, sum) },
			wantMethod: http.MethodPatch, wantPath: "/stream/a.c4gh",
			wantQuery: url.Values{"uploadId": {"u-1"}, "chunk": {"end"}, "fileSize": {"1234"}, "sha256": {sum}},
		},
		{
			name:       "notify",
			call:       func(c *Client) error { return c.Notify(context.Background(), "a.c4gh", 1234, sum) },
			wantMethod: http.MethodPost, wantPath: "/stream/a.c4gh/notify",
			wantQuery: url.Values{"fileSize": {"1234"}, "sha256": {sum}},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c, log := fakeProxy(t, answer(http.StatusOK, ""))
			if err := tt.call(c); err != nil {
				t.Fatal(err)
			}
			req := log.all()[0]
			if req.method != tt.wantMethod || req.path != tt.wantPath {
				t.Errorf("sent %s %s, want %s %s", req.method, req.path, tt.wantMethod, tt.wantPath)
			}
			if req.rawQuery != tt.wantQuery.Encode() {
				t.Errorf("query = %s, want %s", req.rawQuery, tt.wantQuery.Encode())
			}
			if len(req.body) != 0 {
				t.Errorf("body = %q, want none", req.body)
			}
			if _, _, ok := basicAuth(req); !ok {
				t.Error("no CEGA credentials")
			}
		})
	}
}

func TestDownloadNoCEGA(t *testing.T) {
	tests := []struct {
		name     string
		call     func(*Client) error
		wantCEGA bool
	}{
		{name: "download", call: func(c *Client) error {
			body, _, err := c.Download(context.Background(), "a.c4gh")
			if err == nil {
				_, err = io.ReadAll(body)
				body.Close()
			}
			return err
		}},
		{name: "outbox listing", call: func(c *Client) error {
			_, err := c.ListFiles(context.Background(), false, 1, 10)
			return err
		}},
		{name: "inbox listing", wantCEGA: true, call: func(c *Client) error {
			_, err := c.ListFiles(context.Background(), true, 1, 10)
			return err
		}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c, log := fakeProxy(t, answer(http.StatusOK, `{"files":[]}`))
			if err := tt.call(c); err != nil {
				t.Fatal(err)
			}
			req := log.all()[0]
			if got := req.header.Get("Proxy-Authorization"); got != "Bearer "+testToken {
				t.Errorf("Proxy-Authorization = %q", got)
			}
			if got := req.header.Get("Authorization") != ""; got != tt.wantCEGA {
				t.Errorf("Authorization sent = %v, want %v", got, tt.wantCEGA)
			}
		})
	}
}

func TestDownload(t *testing.T) {
	c, log := fakeProxy(t, answer(http.StatusOK, "payload"))
	body, _, err := c.Download(context.Background(), "a.c4gh")
	if err != nil {
		t.Fatal(err)
	}
	defer body.Close()
	got, err := io.ReadAll(body)
	if err != nil || string(got) != "payload" {
		t.Errorf("body = %q, %v", got, err)
	}
	if req := log.all()[0]; req.method != http.MethodGet || req.path != "/stream/a.c4gh" {
		t.Errorf("sent %s %s", req.method, req.path)
	}
}

func TestPathEscaping(t *testing.T) {
	names := []struct{ name, escaped string }{
		{name: "a b.c4gh", escaped: "a%20b.c4gh"},
		{name: "a+b.c4gh", escaped: "a+b.c4gh"},
		{name: "100%.c4gh", escaped: "100%25.c4gh"},
		{name: "prøve.c4gh", escaped: "pr%C3%B8ve.c4gh"},
	}
	calls := []struct {
		route  string
		suffix string
		call   func(c *Client, name string) error
	}{
		{route: "chunk", call: func(c *Client, name string) error {
			_, err := c.PutChunk(context.Background(), name, "", testChunk, []byte("data"))
			return err
		}},
		{route: "finish", call: func(c *Client, name string) error {
			return c.Finish(context.Background(), name, "u-1", 4, "00")
		}},
		{route: "notify", suffix: "/notify", call: func(c *Client, name string) error {
			return c.Notify(context.Background(), name, 4, "00")
		}},
		{route: "download", call: func(c *Client, name string) error {
			body, _, err := c.Download(context.Background(), name)
			if err == nil {
				body.Close()
			}
			return err
		}},
	}
	for _, n := range names {
		for _, call := range calls {
			t.Run(call.route+" "+n.name, func(t *testing.T) {
				var requestURI string
				c, log := fakeProxy(t, func(w http.ResponseWriter, r *http.Request) {
					requestURI = r.RequestURI
					_, _ = io.WriteString(w, `{"id":"u-1"}`)
				})
				if err := call.call(c, n.name); err != nil {
					t.Fatal(err)
				}
				if got, want := log.all()[0].path, "/stream/"+n.name+call.suffix; got != want {
					t.Errorf("decoded path = %q, want %q", got, want)
				}
				if want := "/stream/" + n.escaped + call.suffix; !strings.HasPrefix(requestURI, want) {
					t.Errorf("request URI = %q, want prefix %q", requestURI, want)
				}
			})
		}
	}
}

func basicAuth(s seen) (string, string, bool) {
	r := &http.Request{Header: s.header}
	return r.BasicAuth()
}
