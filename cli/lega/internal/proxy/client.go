package proxy

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/httpx"
)

// Client talks to one proxy on behalf of one user. Token is the ELIXIR AAI
// token sent as Proxy-Authorization; CEGAUsername and CEGAPassword go as
// basic auth on the routes the proxy checks them on.
type Client struct {
	BaseURL      *url.URL
	Token        string
	CEGAUsername string
	CEGAPassword string
	HTTP         *http.Client

	// stallTimeout overrides httpx.StallTimeout, so a test need not wait
	// two minutes for a stall.
	stallTimeout time.Duration
}

// NewClient returns a Client whose transport has the timeouts
// httpx.NewClient sets.
func NewClient(baseURL *url.URL, token, cegaUsername, cegaPassword string, insecureTLS bool) *Client {
	return &Client{
		BaseURL:      baseURL,
		Token:        token,
		CEGAUsername: cegaUsername,
		CEGAPassword: cegaPassword,
		HTTP:         httpx.NewClient(insecureTLS),
	}
}

// request is one call to the proxy. cega marks the routes that check CEGA
// basic auth; bounded marks the calls that get httpx.CallTimeout.
type request struct {
	method   string
	segments []string
	query    url.Values
	body     []byte
	cega     bool
	bounded  bool
}

func (r request) path() string {
	return httpx.Endpoint(&url.URL{}, nil, r.segments...).Path
}

// do sends r and returns the response for a 2xx answer, its body guarded
// against a stall until it is closed. Any other status is returned as an
// *APIError with the body read and closed.
func (c *Client) do(ctx context.Context, r request) (*http.Response, error) {
	guard := httpx.NewStallGuard(ctx, c.stall())
	req, err := c.newRequest(guard, r)
	if err != nil {
		guard.Release()
		return nil, err
	}
	resp, err := c.HTTP.Do(req)
	if err != nil {
		guard.Release()
		if stalled := guard.Err(err); stalled != err {
			return nil, fmt.Errorf("%s %s: %w", r.method, r.path(), stalled)
		}
		return nil, err
	}
	if resp.StatusCode/100 != 2 {
		defer guard.Release()
		return nil, &APIError{Method: r.method, Path: r.path(), Status: resp.StatusCode, Body: httpx.ErrorBody(resp, c.Token, c.CEGAPassword)}
	}
	resp.Body = guard.ResponseBody(resp.Body)
	return resp, nil
}

// newRequest sets ContentLength by hand: the guarded body hides the length
// a byte slice would give, and without it a chunk would go chunked.
func (c *Client) newRequest(guard *httpx.StallGuard, r request) (*http.Request, error) {
	var body io.Reader
	if len(r.body) > 0 {
		body = guard.RequestBody(r.body)
	}
	u := httpx.Endpoint(c.BaseURL, r.query, r.segments...)
	req, err := http.NewRequestWithContext(guard.Context(), r.method, u.String(), body)
	if err != nil {
		return nil, err
	}
	req.ContentLength = int64(len(r.body))
	req.Header.Set("Proxy-Authorization", "Bearer "+c.Token)
	if r.cega && c.CEGAUsername != "" && c.CEGAPassword != "" {
		req.SetBasicAuth(c.CEGAUsername, c.CEGAPassword)
	}
	return req, nil
}

func (c *Client) stall() time.Duration {
	if c.stallTimeout > 0 {
		return c.stallTimeout
	}
	return httpx.StallTimeout
}

// call sends r and decodes a 2xx body into out, or discards it when out is
// nil.
func (c *Client) call(ctx context.Context, r request, out any) error {
	if r.bounded {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, httpx.CallTimeout)
		defer cancel()
	}
	resp, err := c.do(ctx, r)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if out == nil {
		_, err = io.Copy(io.Discard, resp.Body)
		return err
	}
	if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
		return fmt.Errorf("%s %s: read answer: %w", r.method, r.path(), err)
	}
	return nil
}
