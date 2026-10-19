// Package requests contains structure an helper methods for performing HTTP requests towards LocalEGA instance.
package requests

import (
	"crypto/tls"
	"errors"
	"io"
	"log"
	"net/http"
	"strings"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega-commander/conf"
)

// ErrEGAAuthentication means the proxy rejected the CENTRAL_EGA_USERNAME and CENTRAL_EGA_PASSWORD pair.
var ErrEGAAuthentication = errors.New("EGA authentication failed: check CENTRAL_EGA_USERNAME and CENTRAL_EGA_PASSWORD")

// ErrRejectedWithoutReason is a 403 with no body. The proxy's ELIXIR AAI check answers that way both when it
// rejects the token and when anything behind it fails, so the cause cannot be told apart.
var ErrRejectedWithoutReason = errors.New("request rejected by the proxy (403 Forbidden) with no reason given: " +
	"ELIXIR_AAI_TOKEN may be invalid or expired, or the proxy failed to process the request")

// egaAuthenticationFailedBody is the proxy's 403 body for an EGA password that does not match, sent only by
// AAIAspect.authenticateCEGA in services/localega-tsd-proxy. Keep the two in step.
const egaAuthenticationFailedBody = "EGA authentication failed"

// maxBodyBytes caps how much of an error body reaches the user; an HTML error page from a gateway can be long.
const maxBodyBytes = 1024

// RejectedError is a 401 or 403 from the proxy that is not a recognised authentication failure.
// Body carries the proxy's own reason, e.g. CEGA's answer for an unknown EGA username.
type RejectedError struct {
	Status string
	Body   string
}

// Error reports the status and, when the proxy sent one, its reason.
func (e *RejectedError) Error() string {
	if e.Body == "" {
		return "request rejected by the proxy (" + e.Status + ")"
	}
	return "request rejected by the proxy (" + e.Status + "): " + e.Body
}

// CheckResponse returns nil for a 200 OK response. Otherwise it reads up to maxBodyBytes of the body,
// closes it, and returns ErrEGAAuthentication, ErrRejectedWithoutReason or a *RejectedError for a 401
// or 403, and an error carrying the status and body for anything else.
func CheckResponse(response *http.Response) error {
	if response.StatusCode == http.StatusOK {
		return nil
	}
	var body string
	if response.Body != nil {
		raw, _ := io.ReadAll(io.LimitReader(response.Body, maxBodyBytes))
		_ = response.Body.Close()
		body = strings.TrimSpace(string(raw))
	}
	switch {
	case response.StatusCode != http.StatusUnauthorized && response.StatusCode != http.StatusForbidden:
		if body == "" {
			return errors.New(response.Status)
		}
		return errors.New(response.Status + ": " + body)
	case body == egaAuthenticationFailedBody:
		return ErrEGAAuthentication
	case response.StatusCode == http.StatusForbidden && body == "":
		return ErrRejectedWithoutReason
	default:
		return &RejectedError{Status: response.Status, Body: body}
	}
}

// Client is an interface providing DoRequest method for performing HTTP requests towards LocalEGA instance.
type Client interface {
	DoRequest(method string, url string, body io.Reader, headers map[string]string, params map[string]string, username string, password string) (*http.Response, error)
}

type defaultClient struct {
	client http.Client
}

// NewClient constructs Client instance, possibly accepting custom http.Client implementation.
func NewClient(client *http.Client) Client {
	defaultClient := defaultClient{}
	if client != nil {
		defaultClient.client = *client
	} else if conf.NewConfiguration().GetTLSSkipVerify() {
		log.Println("WARNING: LEGA_COMMANDER_TLS_SKIP_VERIFY is enabled – TLS certificate verification is disabled for uploads. This should only be used for testing with trusted servers.")
		defaultClient.client = http.Client{
			Transport: &http.Transport{
				TLSClientConfig: &tls.Config{InsecureSkipVerify: true}, // #nosec G402
			},
		}
	} else {
		defaultClient.client = *http.DefaultClient
	}
	return defaultClient
}

// DoRequest method can perform different HTTP requests with different parameters towards LocalEGA instance.
func (c defaultClient) DoRequest(method, url string, body io.Reader, headers, params map[string]string, username, password string) (*http.Response, error) {
	request, err := http.NewRequest(method, url, body)
	if err != nil {
		return nil, err
	}
	for name, header := range headers {
		request.Header.Add(name, header)
	}
	if params != nil {
		query := request.URL.Query()
		for name, param := range params {
			query.Add(name, param)
		}
		request.URL.RawQuery = query.Encode()
	}

	if username != "" && password != "" {
		request.SetBasicAuth(username, password)
	}

	return c.client.Do(request)
}
