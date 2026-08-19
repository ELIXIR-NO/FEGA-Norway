package report

import (
	"reflect"
	"testing"
)

func TestSanitizeLega(t *testing.T) {
	// Realistic lega-commander capture: duplicated TLS warnings, an ANSI-coloured
	// "Uploading" line, \r-redrawn progress frames, an "Assembling" status line,
	// and final 100% frames, exactly the shape CombinedOutput returns.
	raw := []byte("2025/01/01 00:00:00 WARNING: LEGA_COMMANDER_TLS_SKIP_VERIFY is enabled\n" +
		"2025/01/01 00:00:00 WARNING: LEGA_COMMANDER_TLS_SKIP_VERIFY is enabled\n" +
		"\x1b[34mUploading file: /fega-norway/abc.raw.enc (10490364 bytes)\x1b[0m\n" +
		"0 / 10490364 [____]0.00% ? p/s\r0 / 10490364 [____]0.00% ? p/s\r" +
		"Assembling the uploaded parts of the file together!\n" +
		"10490364 / 10490364 [-->] 100.00% 17489118 p/s\r10490364 / 10490364 [-->]100.00% 11156255 p/s\n")

	got := SanitizeLega(raw)
	want := []string{
		"Uploading file: /fega-norway/abc.raw.enc (10490364 bytes)",
		"Assembling the uploaded parts of the file together!",
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("SanitizeLega mismatch:\n got: %#v\nwant: %#v", got, want)
	}
}
