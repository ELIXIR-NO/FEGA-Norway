// Package httpx is the HTTP client the suite uses to talk to the proxy and the
// DOA. TLS verification is skipped: the stack uses mkcert certificates the
// runner does not otherwise trust.
package httpx

import (
	"bytes"
	"context"
	"crypto/tls"
	"io"
	"net/http"
)

// Client is a TLS-skipping HTTP client.
type Client struct {
	hc *http.Client
}

// Response is a simple status+body pair.
type Response struct {
	Status int
	Body   []byte
}

// New builds a client that skips TLS verification. It sets no overall timeout:
// uploads are large, so per-call deadlines are controlled through the request
// context instead.
func New() *Client {
	return &Client{
		hc: &http.Client{
			Transport: &http.Transport{
				TLSClientConfig: &tls.Config{InsecureSkipVerify: true}, //nolint:gosec // mkcert stack, certs not trusted by the runner
			},
		},
	}
}

// Option configures a request.
type Option func(*http.Request)

// WithBody sets the request body and Content-Type.
func WithBody(body []byte, contentType string) Option {
	return func(r *http.Request) {
		r.Body = io.NopCloser(bytes.NewReader(body))
		r.ContentLength = int64(len(body))
		if contentType != "" {
			r.Header.Set("Content-Type", contentType)
		}
	}
}

// WithBearer sets the Authorization: Bearer header (DOA download path).
func WithBearer(token string) Option {
	return func(r *http.Request) { r.Header.Set("Authorization", "Bearer "+token) }
}

// WithProxyBearer sets the Proxy-Authorization: Bearer header (proxy path).
func WithProxyBearer(token string) Option {
	return func(r *http.Request) { r.Header.Set("Proxy-Authorization", "Bearer "+token) }
}

// WithBasicAuth sets HTTP basic auth (the CEGA / proxy-admin credentials).
func WithBasicAuth(user, pass string) Option {
	return func(r *http.Request) { r.SetBasicAuth(user, pass) }
}

// WithHeader sets an arbitrary header.
func WithHeader(key, value string) Option {
	return func(r *http.Request) { r.Header.Set(key, value) }
}

// Do issues the request and reads the full body into the response.
func (c *Client) Do(ctx context.Context, method, url string, opts ...Option) (*Response, error) {
	req, err := http.NewRequestWithContext(ctx, method, url, nil)
	if err != nil {
		return nil, err
	}
	for _, o := range opts {
		o(req)
	}
	resp, err := c.hc.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	return &Response{Status: resp.StatusCode, Body: body}, nil
}
