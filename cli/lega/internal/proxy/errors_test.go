package proxy

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"testing"
)

func TestAPIErrorCarriesBody(t *testing.T) {
	const mismatch = "Checksum mismatch. Resumable upload interrrupted and can't be resumed. Please, re-upload the whole file."
	tests := []struct {
		name, body string
		status     int
		want       []string
		wantLen    int
	}{
		{name: "checksum mismatch text", status: http.StatusInternalServerError, body: mismatch, want: []string{"PATCH /stream/a.c4gh", "500", mismatch}},
		{name: "bodyless 403 names its three causes", status: http.StatusForbidden, want: []string{"ELIXIR_AAI_TOKEN", "CENTRAL_EGA_PASSWORD", "folder does not exist"}},
		{name: "EGA password", status: http.StatusForbidden, body: "EGA authentication failed", want: []string{"EGA authentication failed", "check CENTRAL_EGA_USERNAME"}},
		{name: "problem detail and request ID", status: http.StatusUnauthorized,
			body: `{"type":"about:blank","title":"Unauthorized","status":401,"detail":"EGA login failed","code":"EGA_AUTH_FAILED","requestId":"r-42"}`,
			want: []string{"EGA login failed", "EGA_AUTH_FAILED", "r-42"}},
		{name: "bodyless 401 says what to check", status: http.StatusUnauthorized, want: []string{"401 Unauthorized", "check ELIXIR_AAI_TOKEN"}},
		{name: "bodyless 503 is an outage", status: http.StatusServiceUnavailable, want: []string{"503", "try again later"}},
		{name: "body echoing the credentials", status: http.StatusBadRequest, body: "bad token " + testToken + " for " + testPassword, want: []string{"bad token [redacted] for [redacted]"}},
		{name: "body capped at 4 KiB", status: http.StatusBadGateway, body: strings.Repeat("x", 10000), wantLen: 4 << 10},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c, _ := fakeProxy(t, answer(tt.status, tt.body))
			_, err := c.PutChunk(context.Background(), "a.c4gh", "", testChunk, []byte("data"))
			var apiErr *APIError
			if !errors.As(err, &apiErr) {
				t.Fatalf("err = %v, want *APIError", err)
			}
			if apiErr.Status != tt.status {
				t.Errorf("Status = %d, want %d", apiErr.Status, tt.status)
			}
			if tt.wantLen > 0 && len(apiErr.Body) != tt.wantLen {
				t.Errorf("len(Body) = %d, want %d", len(apiErr.Body), tt.wantLen)
			}
			msg := err.Error()
			for _, w := range tt.want {
				if !strings.Contains(msg, w) {
					t.Errorf("Error() = %q, missing %q", msg, w)
				}
			}
			for _, secret := range []string{testToken, testPassword, "Basic "} {
				if strings.Contains(msg, secret) {
					t.Errorf("Error() leaks %q: %q", secret, msg)
				}
			}
		})
	}
}

func TestClassify(t *testing.T) {
	problem := func(code string) string {
		return `{"type":"about:blank","status":0,"detail":"d","code":"` + code + `"}`
	}
	tests := []struct {
		name   string
		status int
		body   string
		want   kind
	}{
		{name: "200 [] is empty", status: 200, body: `[]`, want: emptyKind},
		{name: "200 with no files is empty", status: 200, body: `{"files":[]}`, want: emptyKind},
		{name: "200 with files is not empty", status: 200, body: `{"files":[{"fileName":"a"}]}`, want: unknownKind},
		{name: "MISSING_CREDENTIALS", status: 401, body: problem("MISSING_CREDENTIALS"), want: authKind},
		{name: "ELIXIR_AAI_TOKEN_INVALID", status: 401, body: problem("ELIXIR_AAI_TOKEN_INVALID"), want: authKind},
		{name: "EGA_AUTH_FAILED", status: 401, body: problem("EGA_AUTH_FAILED"), want: authKind},
		{name: "VISA_DENIED", status: 403, body: problem("VISA_DENIED"), want: authKind},
		{name: "CEGA_UNAVAILABLE", status: 503, body: problem("CEGA_UNAVAILABLE"), want: outageKind},
		{name: "UPSTREAM_FAILED", status: 502, body: problem("UPSTREAM_FAILED"), want: outageKind},
		{name: "FILE_NOT_FOUND", status: 404, body: problem("FILE_NOT_FOUND"), want: notFoundKind},
		{name: "INTERNAL", status: 500, body: problem("INTERNAL"), want: unknownKind},
		{name: "code wins over status", status: 403, body: problem("UPSTREAM_FAILED"), want: outageKind},
		{name: "unknown code falls back to status", status: 503, body: problem("SOMETHING_NEW"), want: outageKind},
		{name: "bodyless 403", status: 403, want: authKind},
		{name: "bodyless 401", status: 401, want: authKind},
		{name: "EGA password text", status: 403, body: "EGA authentication failed", want: authKind},
		{name: "404", status: 404, want: notFoundKind},
		{name: "502 is an outage", status: 502, want: outageKind},
		{name: "502 is never an empty list", status: 502, body: `[]`, want: outageKind},
		{name: "503", status: 503, want: outageKind},
		{name: "504", status: 504, want: outageKind},
		{name: "500 checksum mismatch", status: 500, body: "Checksum mismatch.", want: unknownKind},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := classify(tt.status, tt.body); got != tt.want {
				t.Errorf("classify(%d, %q) = %v, want %v", tt.status, tt.body, got, tt.want)
			}
		})
	}
}

func TestIsBareRefusal(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want bool
	}{
		{name: "bodyless 403", err: &APIError{Status: 403}, want: true},
		{name: "403 with a code", err: &APIError{Status: 403, Body: `{"code":"VISA_DENIED"}`}},
		{name: "403 with text", err: &APIError{Status: 403, Body: "EGA authentication failed"}},
		{name: "bodyless 401", err: &APIError{Status: 401}},
		{name: "transport error", err: errors.New("boom")},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := IsBareRefusal(tt.err); got != tt.want {
				t.Errorf("IsBareRefusal = %v, want %v", got, tt.want)
			}
		})
	}
}
