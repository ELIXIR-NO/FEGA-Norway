package httpx

import (
	"bytes"
	"crypto/tls"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const (
	connectTimeout = 30 * time.Second
	// headerTimeout is generous because the proxy assembles the whole file
	// before it answers chunk=end.
	headerTimeout = 15 * time.Minute
	// maxErrorBody caps the body kept from a failed call; a gateway's HTML
	// error page can be long.
	maxErrorBody = 4 << 10
)

// CallTimeout bounds a call that moves no file data, such as a listing, a
// delete or a token exchange.
const CallTimeout = 2 * time.Minute

// NewClient returns the HTTP client every lega request goes through. It sets
// no overall Client.Timeout, since one 50 MiB chunk on a slow link
// legitimately takes minutes; dial, TLS and response-header timeouts bound
// the waits that are not data transfer, and a StallGuard bounds the rest.
func NewClient(insecureTLS bool) *http.Client {
	t := http.DefaultTransport.(*http.Transport).Clone()
	t.DialContext = (&net.Dialer{Timeout: connectTimeout, KeepAlive: 30 * time.Second}).DialContext
	t.TLSHandshakeTimeout = connectTimeout
	t.ResponseHeaderTimeout = headerTimeout
	if insecureTLS {
		t.TLSClientConfig = &tls.Config{InsecureSkipVerify: true} // #nosec G402 -- opt-in for test stacks
	}
	return &http.Client{Transport: t}
}

// Endpoint joins escaped path segments onto base. Path and RawPath are set
// together so that a name holding a space, a plus or a percent sign arrives
// as that exact name.
func Endpoint(base *url.URL, query url.Values, segments ...string) *url.URL {
	u := *base
	plain := strings.TrimRight(base.Path, "/")
	escaped := strings.TrimRight(base.EscapedPath(), "/")
	for _, s := range segments {
		plain += "/" + s
		escaped += "/" + url.PathEscape(s)
	}
	u.Path, u.RawPath = plain, escaped
	u.RawQuery = query.Encode()
	return &u
}

// ErrorBody reads at most 4 KiB of a failed answer's body, trimmed, and
// closes it. Every non-empty secret is blanked out, so a server that echoes
// a token back cannot put it in an error message.
func ErrorBody(resp *http.Response, secrets ...string) string {
	defer resp.Body.Close()
	raw, _ := io.ReadAll(io.LimitReader(resp.Body, maxErrorBody))
	body := string(bytes.TrimSpace(raw))
	for _, s := range secrets {
		if s != "" {
			body = strings.ReplaceAll(body, s, "[redacted]")
		}
	}
	return body
}
