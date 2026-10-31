package httpx

import (
	"net/http"
	"net/url"
	"testing"
)

func TestEndpoint(t *testing.T) {
	tests := []struct {
		name, base string
		segments   []string
		query      url.Values
		want       string
	}{
		{name: "root base", base: "https://ega.example", segments: []string{"files"}, want: "https://ega.example/files"},
		{name: "trailing slash", base: "https://ega.example/", segments: []string{"files"}, want: "https://ega.example/files"},
		{name: "base with a path", base: "https://ega.example/proxy/", segments: []string{"stream", "a b"}, want: "https://ega.example/proxy/stream/a%20b"},
		{name: "slash in a segment", base: "https://ega.example", segments: []string{"stream", "a/b"}, want: "https://ega.example/stream/a%2Fb"},
		{name: "query", base: "https://ega.example", segments: []string{"files"}, query: url.Values{"fileName": {"a+b"}}, want: "https://ega.example/files?fileName=a%2Bb"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			base, err := url.Parse(tt.base)
			if err != nil {
				t.Fatal(err)
			}
			if got := Endpoint(base, tt.query, tt.segments...).String(); got != tt.want {
				t.Errorf("Endpoint = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestNewClient(t *testing.T) {
	tests := []struct {
		name     string
		insecure bool
	}{
		{name: "verifies TLS"},
		{name: "skips TLS verification", insecure: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			hc := NewClient(tt.insecure)
			tr := hc.Transport.(*http.Transport)
			if hc.Timeout != 0 {
				t.Errorf("Client.Timeout = %v, want none", hc.Timeout)
			}
			if tr.ResponseHeaderTimeout != headerTimeout || tr.TLSHandshakeTimeout != connectTimeout {
				t.Errorf("timeouts = %v, %v", tr.ResponseHeaderTimeout, tr.TLSHandshakeTimeout)
			}
			skips := tr.TLSClientConfig != nil && tr.TLSClientConfig.InsecureSkipVerify
			if skips != tt.insecure {
				t.Errorf("InsecureSkipVerify = %v, want %v", skips, tt.insecure)
			}
		})
	}
}
