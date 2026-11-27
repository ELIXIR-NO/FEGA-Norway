package config

import (
	"errors"
	"reflect"
	"strings"
	"testing"
)

func env(vars map[string]string) func(string) string {
	return func(name string) string { return vars[name] }
}

func allSet() map[string]string {
	return map[string]string{
		envLocalEGAInstanceURL: "https://proxy.test",
		envElixirAAIToken:      "aai-token",
		envCentralEGAUsername:  "cega-user",
		envCentralEGAPassword:  "cega-pass",
		envTSDProxyURL:         "https://tsd.test",
		envTSDProjName:         "p11",
		envChunkSize:           "8",
		envTLSSkipVerify:       "true",
	}
}

func with(name, value string) map[string]string {
	vars := allSet()
	vars[name] = value
	return vars
}

func TestLoad(t *testing.T) {
	all := Token | CEGA | TSD
	tests := []struct {
		name     string
		vars     map[string]string
		need     Need
		check    func(t *testing.T, c Config)
		wantVars []string
	}{
		{
			name: "all set",
			vars: allSet(),
			need: all,
			check: func(t *testing.T, c Config) {
				want := Config{
					ElixirAAIToken:     "aai-token",
					CentralEGAUsername: "cega-user",
					CentralEGAPassword: "cega-pass",
					TSDProjName:        "p11",
					ChunkSize:          8,
					TLSSkipVerify:      true,
				}
				if got := c.LocalEGAInstanceURL.String(); got != "https://proxy.test" {
					t.Errorf("LocalEGAInstanceURL = %q", got)
				}
				if got := c.TSDProxyURL.String(); got != "https://tsd.test" {
					t.Errorf("TSDProxyURL = %q", got)
				}
				c.LocalEGAInstanceURL, c.TSDProxyURL = nil, nil
				if !reflect.DeepEqual(c, want) {
					t.Errorf("config = %+v, want %+v", c, want)
				}
			},
		},
		{
			name: "defaults",
			vars: map[string]string{envElixirAAIToken: "aai-token"},
			need: Token | TSD,
			check: func(t *testing.T, c Config) {
				if got := c.LocalEGAInstanceURL.String(); got != defaultInstanceURL {
					t.Errorf("LocalEGAInstanceURL = %q", got)
				}
				if got := c.TSDProxyURL.String(); got != defaultTSDProxyURL {
					t.Errorf("TSDProxyURL = %q", got)
				}
				if c.TSDProjName != defaultTSDProjName || c.TLSSkipVerify {
					t.Errorf("TSDProjName = %q, TLSSkipVerify = %v", c.TSDProjName, c.TLSSkipVerify)
				}
			},
		},
		{name: "token missing", vars: with(envElixirAAIToken, ""), need: Token, wantVars: []string{envElixirAAIToken}},
		{name: "cega username missing", vars: with(envCentralEGAUsername, ""), need: CEGA, wantVars: []string{envCentralEGAUsername}},
		{name: "cega password missing", vars: with(envCentralEGAPassword, ""), need: CEGA, wantVars: []string{envCentralEGAPassword}},
		{
			name:     "every missing var reported at once",
			vars:     map[string]string{},
			need:     all,
			wantVars: []string{envElixirAAIToken, envCentralEGAUsername, envCentralEGAPassword},
		},
		{
			name: "tsd not needed",
			vars: map[string]string{envElixirAAIToken: "aai-token"},
			need: Token,
			check: func(t *testing.T, c Config) {
				if c.TSDProxyURL != nil || c.TSDProjName != "" {
					t.Errorf("TSD loaded without being needed: %v %q", c.TSDProxyURL, c.TSDProjName)
				}
			},
		},
		{name: "token not needed", vars: map[string]string{}, need: 0},
		{name: "chunk 0", vars: with(envChunkSize, "0"), need: all, wantVars: []string{envChunkSize}},
		{name: "chunk -1", vars: with(envChunkSize, "-1"), need: all, wantVars: []string{envChunkSize}},
		{name: "chunk 257", vars: with(envChunkSize, "257"), need: all, wantVars: []string{envChunkSize}},
		{name: "chunk abc", vars: with(envChunkSize, "abc"), need: all, wantVars: []string{envChunkSize}},
		{
			name:  "chunk 256",
			vars:  with(envChunkSize, "256"),
			need:  all,
			check: wantChunk(256),
		},
		{
			name:  "chunk unset",
			vars:  with(envChunkSize, ""),
			need:  all,
			check: wantChunk(50),
		},
		{
			name: "http URL",
			vars: with(envLocalEGAInstanceURL, "http://localhost:8080"),
			need: all,
			check: func(t *testing.T, c Config) {
				if !reflect.DeepEqual(c.Insecure, []string{envLocalEGAInstanceURL}) {
					t.Errorf("Insecure = %v", c.Insecure)
				}
			},
		},
		{
			name: "http TSD URL ignored when TSD not needed",
			vars: with(envTSDProxyURL, "http://localhost:8081"),
			need: Token | CEGA,
			check: func(t *testing.T, c Config) {
				if len(c.Insecure) != 0 {
					t.Errorf("Insecure = %v", c.Insecure)
				}
			},
		},
		{
			name: "trailing slash",
			vars: with(envLocalEGAInstanceURL, "https://proxy.test/base//"),
			need: all,
			check: func(t *testing.T, c Config) {
				if got := c.LocalEGAInstanceURL.String(); got != "https://proxy.test/base" {
					t.Errorf("LocalEGAInstanceURL = %q", got)
				}
			},
		},
		{name: "URL without scheme", vars: with(envLocalEGAInstanceURL, "proxy.test"), need: all, wantVars: []string{envLocalEGAInstanceURL}},
		{name: "URL with other scheme", vars: with(envTSDProxyURL, "ftp://tsd.test"), need: all, wantVars: []string{envTSDProxyURL}},
		{name: "TLS skip only on true", vars: with(envTLSSkipVerify, "1"), need: all, check: func(t *testing.T, c Config) {
			if c.TLSSkipVerify {
				t.Error("TLSSkipVerify = true for \"1\"")
			}
		}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c, err := Load(env(tt.vars), tt.need)
			if tt.wantVars == nil {
				if err != nil {
					t.Fatalf("err = %v, want nil", err)
				}
				if tt.check != nil {
					tt.check(t, c)
				}
				return
			}
			var e *Error
			if !errors.As(err, &e) {
				t.Fatalf("err = %v, want *Error", err)
			}
			if got := problemVars(e); !reflect.DeepEqual(got, tt.wantVars) {
				t.Errorf("variables at fault = %v, want %v", got, tt.wantVars)
			}
			for _, name := range tt.wantVars {
				if !strings.Contains(err.Error(), name) {
					t.Errorf("message %q does not name %s", err, name)
				}
			}
		})
	}
}

func wantChunk(mib int64) func(*testing.T, Config) {
	return func(t *testing.T, c Config) {
		if c.ChunkSize != mib || c.ChunkBytes() != mib<<20 {
			t.Errorf("ChunkSize = %d, ChunkBytes = %d, want %d MiB", c.ChunkSize, c.ChunkBytes(), mib)
		}
	}
}

func TestErrorNeverQuotesSecrets(t *testing.T) {
	vars := with(envLocalEGAInstanceURL, "ftp://user:hunter2@proxy.test")
	vars[envChunkSize] = "0"
	_, err := Load(env(vars), Token|CEGA|TSD)
	if err == nil {
		t.Fatal("err = nil")
	}
	for _, secret := range []string{"hunter2", "aai-token", "cega-pass"} {
		if strings.Contains(err.Error(), secret) {
			t.Errorf("message %q quotes %q", err, secret)
		}
	}
}

func problemVars(e *Error) []string {
	vars := make([]string, len(e.Problems))
	for i, p := range e.Problems {
		vars[i] = p.Var
	}
	return vars
}
