package proxy

import (
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"sync"
	"testing"
)

const (
	testToken    = "test-elixir-token"
	testUser     = "test-ega-user"
	testPassword = "test-ega-password"
)

// seen is what a fake proxy recorded about one request.
type seen struct {
	method, path, rawQuery string
	query                  url.Values
	header                 http.Header
	body                   []byte
}

// recorder holds the requests a fake proxy received.
type recorder struct {
	mu   sync.Mutex
	reqs []seen
}

func (r *recorder) add(s seen) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.reqs = append(r.reqs, s)
}

func (r *recorder) all() []seen {
	r.mu.Lock()
	defer r.mu.Unlock()
	return append([]seen(nil), r.reqs...)
}

// fakeProxy answers every request with handler and records it.
func fakeProxy(t *testing.T, handler func(w http.ResponseWriter, r *http.Request)) (*Client, *recorder) {
	t.Helper()
	log := &recorder{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		log.add(seen{
			method: r.Method, path: r.URL.Path, rawQuery: r.URL.RawQuery,
			query: r.URL.Query(), header: r.Header.Clone(), body: body,
		})
		handler(w, r)
	}))
	t.Cleanup(srv.Close)
	base, err := url.Parse(srv.URL + "/")
	if err != nil {
		t.Fatal(err)
	}
	c := NewClient(base, testToken, testUser, testPassword, false)
	return c, log
}

func answer(status int, body string) func(http.ResponseWriter, *http.Request) {
	return func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(status)
		_, _ = io.WriteString(w, body)
	}
}
