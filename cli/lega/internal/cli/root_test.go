package cli

import (
	"bytes"
	"context"
	"strings"
	"testing"
)

func TestRun(t *testing.T) {
	tests := []struct {
		name       string
		args       []string
		wantCode   int
		wantStdout []string
		wantStderr []string
		notStdout  []string
	}{
		{
			name:       "help",
			args:       []string{"--help"},
			wantCode:   ExitOK,
			wantStdout: []string{"Usage:\n  lega [OPTIONS] <", "--json", "--version", "version", "Environment:", "LEGA_COMMANDER_CHUNK_SIZE"},
		},
		{
			name:       "command help has no environment table",
			args:       []string{"version", "--help"},
			wantCode:   ExitOK,
			wantStdout: []string{"lega [OPTIONS] version"},
			notStdout:  []string{"Environment:"},
		},
		{
			name:       "version command",
			args:       []string{"version"},
			wantCode:   ExitOK,
			wantStdout: []string{"lega dev (commit none, built unknown)"},
		},
		{
			name:       "version flag alone",
			args:       []string{"-v"},
			wantCode:   ExitOK,
			wantStdout: []string{"lega dev"},
		},
		{
			name:       "version flag before a command",
			args:       []string{"--version", "version"},
			wantCode:   ExitOK,
			wantStdout: []string{"lega dev"},
		},
		{
			name:       "version with an argument",
			args:       []string{"version", "extra"},
			wantCode:   ExitUsage,
			wantStderr: []string{"lega: version takes no arguments"},
		},
		{
			name:       "unknown command",
			args:       []string{"bogus"},
			wantCode:   ExitUsage,
			wantStderr: []string{"lega: ", "bogus"},
		},
		{
			name:       "unknown flag",
			args:       []string{"--bogus"},
			wantCode:   ExitUsage,
			wantStderr: []string{"lega: unknown flag `bogus'"},
		},
		{
			name:       "no command",
			args:       nil,
			wantCode:   ExitUsage,
			wantStderr: []string{"lega: "},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var stdout, stderr bytes.Buffer
			code := Run(context.Background(), tt.args, func(string) string { return "" }, &stdout, &stderr)
			if code != tt.wantCode {
				t.Errorf("exit code = %d, want %d\nstdout: %s\nstderr: %s", code, tt.wantCode, stdout.String(), stderr.String())
			}
			assertContains(t, "stdout", stdout.String(), tt.wantStdout)
			assertContains(t, "stderr", stderr.String(), tt.wantStderr)
			for _, s := range tt.notStdout {
				if strings.Contains(stdout.String(), s) {
					t.Errorf("stdout contains %q:\n%s", s, stdout.String())
				}
			}
			if tt.wantCode == ExitOK && stderr.Len() > 0 {
				t.Errorf("stderr not empty on success: %s", stderr.String())
			}
		})
	}
}

func assertContains(t *testing.T, stream, got string, want []string) {
	t.Helper()
	for _, s := range want {
		if !strings.Contains(got, s) {
			t.Errorf("%s does not contain %q:\n%s", stream, s, got)
		}
	}
}
