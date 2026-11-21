package proxy

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
)

// APIError is a non-2xx answer from the proxy. Path is the route without
// its query, and Body is at most 4 KiB of what the proxy said.
type APIError struct {
	Method string
	Path   string
	Status int
	Body   string
}

// egaAuthFailedBody is the whole body the proxy's CEGA check sends for a
// wrong EGA password, before the proxy has error codes.
const egaAuthFailedBody = "EGA authentication failed"

// bareRefusalHint explains a 403 without a body: the proxy's ELIXIR AAI
// check answers any failure behind it that way, so the cause cannot be
// told apart until the proxy sends error codes (FEGA-Norway#913).
const bareRefusalHint = "the proxy refused without a reason; ELIXIR_AAI_TOKEN may be invalid or expired, " +
	"CENTRAL_EGA_USERNAME or CENTRAL_EGA_PASSWORD may be wrong, or your folder does not exist yet"

// kindHints say what to do about a bodyless answer of each kind.
var kindHints = map[kind]string{
	authKind:   "check ELIXIR_AAI_TOKEN, CENTRAL_EGA_USERNAME and CENTRAL_EGA_PASSWORD",
	outageKind: "the service is unavailable; try again later",
}

// Error describes the failed call and what the proxy said. Headers are
// never part of it.
func (e *APIError) Error() string {
	head := fmt.Sprintf("%s %s: %d %s", e.Method, e.Path, e.Status, http.StatusText(e.Status))
	if reason := e.reason(); reason != "" {
		return head + ": " + reason
	}
	return head
}

// HTTPStatus returns the answer's status code.
func (e *APIError) HTTPStatus() int { return e.Status }

func (e *APIError) reason() string {
	if p, ok := parseProblem(e.Body); ok {
		return p.describe()
	}
	switch {
	case e.bareRefusal():
		return bareRefusalHint
	case e.Body == "":
		return kindHints[classify(e.Status, e.Body)]
	case e.Body == egaAuthFailedBody:
		return e.Body + "; check CENTRAL_EGA_USERNAME and CENTRAL_EGA_PASSWORD"
	default:
		return e.Body
	}
}

func (e *APIError) bareRefusal() bool {
	return e.Status == http.StatusForbidden && e.Body == ""
}

// IsBareRefusal reports whether err is a 403 with no body and no error
// code. Only the duplicate-check listing treats it as an empty inbox: until
// the proxy sends codes, it is also what a first-time user's missing
// folder looks like.
func IsBareRefusal(err error) bool {
	var apiErr *APIError
	return errors.As(err, &apiErr) && apiErr.bareRefusal()
}

// kind is what an answer means for the user, independent of how the proxy
// phrased it.
type kind int

// emptyKind is an empty listing; outageKind is the proxy, CEGA or TSD being
// unreachable or failing.
const (
	unknownKind kind = iota
	emptyKind
	authKind
	notFoundKind
	outageKind
)

// problemKinds maps the proxy's problem+json codes to kinds.
var problemKinds = map[string]kind{
	"MISSING_CREDENTIALS":      authKind,
	"ELIXIR_AAI_TOKEN_INVALID": authKind,
	"EGA_AUTH_FAILED":          authKind,
	"VISA_DENIED":              authKind,
	"CEGA_UNAVAILABLE":         outageKind,
	"UPSTREAM_FAILED":          outageKind,
	"FILE_NOT_FOUND":           notFoundKind,
	"INTERNAL":                 unknownKind,
}

// classify decides the kind of one answer. A problem+json code wins; a
// proxy that sends none is judged by its status, and no message text is
// ever matched. A 500 stays unknown: the proxy answers a chunk checksum
// mismatch that way.
func classify(status int, body string) kind {
	if p, ok := parseProblem(body); ok {
		if k, known := problemKinds[p.Code]; known {
			return k
		}
	}
	switch {
	case status/100 == 2 && isEmptyListing(body):
		return emptyKind
	case status == http.StatusUnauthorized, status == http.StatusForbidden:
		return authKind
	case status == http.StatusNotFound:
		return notFoundKind
	case status == http.StatusBadGateway, status == http.StatusServiceUnavailable, status == http.StatusGatewayTimeout:
		return outageKind
	default:
		return unknownKind
	}
}

// problem is the RFC 9457 body the proxy sends with an error code.
type problem struct {
	Code      string `json:"code"`
	Detail    string `json:"detail"`
	Title     string `json:"title"`
	RequestID string `json:"requestId"`
}

func parseProblem(body string) (problem, bool) {
	var p problem
	if !strings.HasPrefix(strings.TrimSpace(body), "{") || json.Unmarshal([]byte(body), &p) != nil {
		return problem{}, false
	}
	return p, p.Code != ""
}

// describe shows the request ID so that a user's report can be matched to
// the proxy's logs.
func (p problem) describe() string {
	text := p.Detail
	if text == "" {
		text = p.Title
	}
	text = strings.TrimSpace(text + " (" + p.Code)
	if p.RequestID != "" {
		text += ", request " + p.RequestID
	}
	return text + ")"
}
