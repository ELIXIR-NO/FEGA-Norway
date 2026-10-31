package tsd

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/httpx"
	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// Token is a TSD access token and the user claim read from it, which names
// the user's upload folder.
type Token struct {
	Bearer string
	User   string
}

// String names the user only, so that printing a Token never prints the
// bearer token.
func (t Token) String() string { return "TSD token for " + t.User }

// GoString keeps %#v from printing the bearer token.
func (t Token) GoString() string { return t.String() }

// Exchange trades an ELIXIR AAI token for a TSD token on the lifesc
// endpoint of tsdProxyURL. The chunks must go to the same host, because TSD
// binds the token to the IP that asked for it (see FEGA-Norway#780).
func Exchange(ctx context.Context, hc *http.Client, tsdProxyURL *url.URL, proj, elixirToken string) (Token, error) {
	ctx, cancel := context.WithTimeout(ctx, httpx.CallTimeout)
	defer cancel()
	body, err := json.Marshal(struct {
		IDToken string `json:"idtoken"`
	}{elixirToken})
	if err != nil {
		return Token{}, err
	}
	u := httpx.Endpoint(tsdProxyURL, url.Values{"type": {"elixir"}}, "v1", proj, "auth", "lifesc", "token")
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u.String(), bytes.NewReader(body))
	if err != nil {
		return Token{}, err
	}
	req.Header.Set("Content-Type", "application/json")
	var answer struct {
		Token string `json:"token"`
	}
	if err := send(hc, req, &answer, elixirToken); err != nil {
		return Token{}, err
	}
	user, err := userClaim(answer.Token)
	if err != nil {
		return Token{}, err
	}
	return Token{Bearer: answer.Token, User: user}, nil
}

// Client sends chunks straight to the TSD file API. It neither lists nor
// notifies: in direct mode those calls still go through the proxy.
type Client struct {
	BaseURL *url.URL
	Project string
	Token   Token
	HTTP    *http.Client

	// stallTimeout overrides httpx.StallTimeout, so a test need not wait
	// two minutes for a stall.
	stallTimeout time.Duration
}

// PutChunk sends one chunk of fileName and returns the upload ID TSD
// assigns. An empty uploadID starts a new upload. TSD takes no per-chunk
// checksum.
func (c *Client) PutChunk(ctx context.Context, fileName, uploadID string, chunk plan.Chunk, data []byte) (string, error) {
	q := url.Values{"chunk": {strconv.FormatInt(chunk.Num, 10)}}
	if uploadID != "" {
		q.Set("id", uploadID)
	}
	var answer struct {
		ID string `json:"id"`
	}
	if err := c.patch(ctx, fileName, q, data, &answer); err != nil {
		return "", err
	}
	if answer.ID == "" {
		return "", fmt.Errorf("PATCH %s: chunk %d: TSD returned no upload ID", fileName, chunk.Num)
	}
	return answer.ID, nil
}

// Finish asks TSD to assemble the upload. It publishes nothing; the caller
// notifies the proxy afterwards.
func (c *Client) Finish(ctx context.Context, fileName, uploadID string) error {
	return c.patch(ctx, fileName, url.Values{"id": {uploadID}, "chunk": {"end"}}, nil, nil)
}

// patch guards a chunk against a stall. ContentLength is set by hand
// because the guarded body hides the length a byte slice would give.
func (c *Client) patch(ctx context.Context, fileName string, q url.Values, data []byte, out any) error {
	guard := httpx.NewStallGuard(ctx, c.stall())
	defer guard.Release()
	var body io.Reader
	if len(data) > 0 {
		body = guard.RequestBody(data)
	}
	u := httpx.Endpoint(c.BaseURL, q, "v1", c.Project, "ega", c.Token.User, "files", fileName)
	req, err := http.NewRequestWithContext(guard.Context(), http.MethodPatch, u.String(), body)
	if err != nil {
		return err
	}
	req.ContentLength = int64(len(data))
	req.Header.Set("Authorization", "Bearer "+c.Token.Bearer)
	err = send(c.HTTP, req, out, c.Token.Bearer)
	if stalled := guard.Err(err); stalled != err {
		return fmt.Errorf("PATCH %s: %w", req.URL.Path, stalled)
	}
	return err
}

func (c *Client) stall() time.Duration {
	if c.stallTimeout > 0 {
		return c.stallTimeout
	}
	return httpx.StallTimeout
}

// send runs req and decodes a 2xx body into out, or discards it when out
// is nil. secrets are blanked out of an error body.
func send(hc *http.Client, req *http.Request, out any, secrets ...string) error {
	resp, err := hc.Do(req)
	if err != nil {
		return err
	}
	if resp.StatusCode/100 != 2 {
		return &APIError{Method: req.Method, Path: req.URL.Path, Status: resp.StatusCode, Body: httpx.ErrorBody(resp, secrets...)}
	}
	defer resp.Body.Close()
	if out == nil {
		_, err = io.Copy(io.Discard, resp.Body)
		return err
	}
	if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
		return fmt.Errorf("%s %s: read answer: %w", req.Method, req.URL.Path, err)
	}
	return nil
}

// APIError is a non-2xx answer from TSD. Path is the request path without
// its query, and Body is at most 4 KiB of what TSD said. Headers are never
// part of it.
type APIError struct {
	Method string
	Path   string
	Status int
	Body   string
}

// Error describes the failed call and what TSD said.
func (e *APIError) Error() string {
	head := fmt.Sprintf("%s %s: %d %s", e.Method, e.Path, e.Status, http.StatusText(e.Status))
	if e.Body == "" {
		return head
	}
	return head + ": " + e.Body
}

// HTTPStatus returns the answer's status code.
func (e *APIError) HTTPStatus() int { return e.Status }
