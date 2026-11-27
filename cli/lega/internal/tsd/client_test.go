package tsd

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync"
	"testing"

	"github.com/golang-jwt/jwt/v5"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// received is what the fake TSD saw on its last request.
type received struct {
	mu                                    sync.Mutex
	method, path, rawQuery, uri, auth, ct string
	body                                  []byte
}

func (r *received) record(req *http.Request) {
	body, _ := io.ReadAll(req.Body)
	r.mu.Lock()
	defer r.mu.Unlock()
	r.method, r.path, r.rawQuery, r.uri = req.Method, req.URL.Path, req.URL.RawQuery, req.RequestURI
	r.auth, r.ct, r.body = req.Header.Get("Authorization"), req.Header.Get("Content-Type"), body
}

func (r *received) snapshot() received {
	r.mu.Lock()
	defer r.mu.Unlock()
	return received{method: r.method, path: r.path, rawQuery: r.rawQuery, uri: r.uri, auth: r.auth, ct: r.ct, body: r.body}
}

func fakeTSD(t *testing.T, status int, body string) (*url.URL, *received) {
	t.Helper()
	got := &received{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got.record(r)
		w.WriteHeader(status)
		_, _ = io.WriteString(w, body)
	}))
	t.Cleanup(srv.Close)
	u, err := url.Parse(srv.URL + "/")
	if err != nil {
		t.Fatal(err)
	}
	return u, got
}

func TestExchange(t *testing.T) {
	good := signed(t, jwt.MapClaims{"user": "p969-ega-user"})
	tests := []struct {
		name     string
		status   int
		body     string
		wantUser string
		wantErr  string
	}{
		{name: "ok", status: 200, body: fmt.Sprintf(`{"statusCode":200,"statusText":"OK","token":%q}`, good), wantUser: "p969-ega-user"},
		{name: "no user claim", status: 200, body: fmt.Sprintf(`{"token":%q}`, signed(t, jwt.MapClaims{"sub": "x"})), wantErr: "no user claim"},
		{name: "malformed token", status: 200, body: `{"token":"not-a-jwt"}`, wantErr: "read TSD token"},
		{name: "no token", status: 200, body: `{}`, wantErr: "read TSD token"},
		{name: "non-200 with body", status: 401, body: `{"message":"invalid id token"}`, wantErr: "401 Unauthorized: {\"message\":\"invalid id token\"}"},
		{name: "non-200 echoing the token", status: 400, body: `{"message":"bad idtoken elixir-token"}`, wantErr: "bad idtoken [redacted]"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			base, got := fakeTSD(t, tt.status, tt.body)
			tok, err := Exchange(context.Background(), http.DefaultClient, base, "p969", "elixir-token")
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v, want it to contain %q", err, tt.wantErr)
				}
			} else if err != nil {
				t.Fatal(err)
			}
			if err != nil && strings.Contains(err.Error(), "elixir-token") {
				t.Errorf("error quotes the ELIXIR token: %v", err)
			}
			if tok.User != tt.wantUser {
				t.Errorf("User = %q, want %q", tok.User, tt.wantUser)
			}
			req := got.snapshot()
			if req.method != http.MethodPost || req.path != "/v1/p969/auth/lifesc/token" || req.rawQuery != "type=elixir" {
				t.Errorf("sent %s %s?%s", req.method, req.path, req.rawQuery)
			}
			var sent map[string]string
			if err := json.Unmarshal(req.body, &sent); err != nil || sent["idtoken"] != "elixir-token" || len(sent) != 1 {
				t.Errorf("body = %s", req.body)
			}
			if req.ct != "application/json" {
				t.Errorf("Content-Type = %q", req.ct)
			}
		})
	}
}

func TestExchangeEscapesToken(t *testing.T) {
	base, got := fakeTSD(t, 200, `{}`)
	_, _ = Exchange(context.Background(), http.DefaultClient, base, "p969", `a"b\c`)
	var sent map[string]string
	if err := json.Unmarshal(got.snapshot().body, &sent); err != nil || sent["idtoken"] != `a"b\c` {
		t.Errorf("body = %s, err %v", got.snapshot().body, err)
	}
}

func TestTokenHidesBearer(t *testing.T) {
	tok := Token{Bearer: "secret-bearer", User: "u"}
	for _, format := range []string{"%v", "%+v", "%#v", "%s"} {
		if s := fmt.Sprintf(format, tok); strings.Contains(s, "secret-bearer") {
			t.Errorf("%s prints the bearer: %s", format, s)
		}
	}
}

func testClient(base *url.URL) *Client {
	return &Client{BaseURL: base, Project: "p969", Token: Token{Bearer: "tsd-bearer", User: "p969-ega-user"}, HTTP: http.DefaultClient}
}

func TestPutChunk(t *testing.T) {
	tests := []struct {
		name, uploadID string
		chunk          plan.Chunk
		status         int
		answer         string
		wantQuery      string
		wantID         string
		wantErr        bool
	}{
		{name: "first chunk", chunk: plan.Chunk{Num: 1, Len: 4}, status: 201, answer: `{"id":"t-1","filename":"a.c4gh","max_chunk":"1"}`, wantQuery: "chunk=1", wantID: "t-1"},
		{name: "later chunk", uploadID: "t-1", chunk: plan.Chunk{Num: 2, Offset: 4, Len: 4}, status: 200, answer: `{"id":"t-1"}`, wantQuery: "chunk=2&id=t-1", wantID: "t-1"},
		{name: "no ID in the answer", chunk: plan.Chunk{Num: 1, Len: 4}, status: 201, answer: `{}`, wantQuery: "chunk=1", wantErr: true},
		{name: "chunk order rejected", uploadID: "t-1", chunk: plan.Chunk{Num: 2, Len: 4}, status: 400, answer: `{"message":"chunk_order_incorrect"}`, wantQuery: "chunk=2&id=t-1", wantErr: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			base, got := fakeTSD(t, tt.status, tt.answer)
			id, err := testClient(base).PutChunk(context.Background(), "a.c4gh", tt.uploadID, tt.chunk, []byte("data"))
			if (err != nil) != tt.wantErr {
				t.Fatalf("err = %v, wantErr %v", err, tt.wantErr)
			}
			if id != tt.wantID {
				t.Errorf("id = %q, want %q", id, tt.wantID)
			}
			req := got.snapshot()
			if req.method != http.MethodPatch || req.path != "/v1/p969/ega/p969-ega-user/files/a.c4gh" || req.rawQuery != tt.wantQuery {
				t.Errorf("sent %s %s?%s", req.method, req.path, req.rawQuery)
			}
			if req.auth != "Bearer tsd-bearer" || string(req.body) != "data" {
				t.Errorf("auth %q, body %q", req.auth, req.body)
			}
		})
	}
}

func TestFinish(t *testing.T) {
	base, got := fakeTSD(t, 200, `{"message":"ok"}`)
	if err := testClient(base).Finish(context.Background(), "a.c4gh", "t-1"); err != nil {
		t.Fatal(err)
	}
	req := got.snapshot()
	if req.method != http.MethodPatch || req.path != "/v1/p969/ega/p969-ega-user/files/a.c4gh" || req.rawQuery != "chunk=end&id=t-1" || len(req.body) != 0 {
		t.Errorf("sent %s %s?%s with %d bytes", req.method, req.path, req.rawQuery, len(req.body))
	}
}

func TestAPIErrorCarriesBody(t *testing.T) {
	base, _ := fakeTSD(t, 500, strings.Repeat("x", 10000))
	err := testClient(base).Finish(context.Background(), "a.c4gh", "t-1")
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Status != 500 || len(apiErr.Body) != 4<<10 {
		t.Fatalf("err = %v", err)
	}
	if strings.Contains(err.Error(), "tsd-bearer") {
		t.Error("error leaks the bearer token")
	}
}

func TestPathEscaping(t *testing.T) {
	tests := []struct{ name, escaped string }{
		{name: "a b.c4gh", escaped: "a%20b.c4gh"},
		{name: "a+b.c4gh", escaped: "a+b.c4gh"},
		{name: "100%.c4gh", escaped: "100%25.c4gh"},
		{name: "prøve.c4gh", escaped: "pr%C3%B8ve.c4gh"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			base, got := fakeTSD(t, 200, `{"id":"t-1"}`)
			if _, err := testClient(base).PutChunk(context.Background(), tt.name, "", plan.Chunk{Num: 1}, []byte("d")); err != nil {
				t.Fatal(err)
			}
			req := got.snapshot()
			prefix := "/v1/p969/ega/p969-ega-user/files/"
			if req.path != prefix+tt.name {
				t.Errorf("decoded path = %q, want %q", req.path, prefix+tt.name)
			}
			if !strings.HasPrefix(req.uri, prefix+tt.escaped+"?") {
				t.Errorf("request URI = %q, want prefix %q", req.uri, prefix+tt.escaped)
			}
		})
	}
}
