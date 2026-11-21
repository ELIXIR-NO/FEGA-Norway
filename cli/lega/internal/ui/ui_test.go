package ui

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestNoColor(t *testing.T) {
	tests := []struct {
		name string
		env  map[string]string
		want bool
	}{
		{name: "unset", env: map[string]string{}},
		{name: "empty", env: map[string]string{"NO_COLOR": ""}},
		{name: "1", env: map[string]string{"NO_COLOR": "1"}, want: true},
		{name: "any text", env: map[string]string{"NO_COLOR": "false"}, want: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			getenv := func(k string) string { return tt.env[k] }
			if got := NoColor(getenv); got != tt.want {
				t.Errorf("NoColor = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestIsTTY(t *testing.T) {
	regular, err := os.Create(filepath.Join(t.TempDir(), "out"))
	if err != nil {
		t.Fatal(err)
	}
	defer regular.Close()
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	defer r.Close()
	defer w.Close()
	closed, err := os.Create(filepath.Join(t.TempDir(), "closed"))
	if err != nil {
		t.Fatal(err)
	}
	closed.Close()
	tests := []struct {
		name string
		f    *os.File
	}{
		{name: "nil"},
		{name: "regular file", f: regular},
		{name: "pipe", f: w},
		{name: "closed file", f: closed},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if IsTTY(tt.f) {
				t.Error("IsTTY = true, want false")
			}
		})
	}
}

func TestHumanSize(t *testing.T) {
	tests := []struct {
		in   int64
		want string
	}{
		{0, "0 B"},
		{1023, "1023 B"},
		{1024, "1.0 KiB"},
		{1536, "1.5 KiB"},
		{1048575, "1.0 MiB"},
		{1028024115, "980.4 MiB"},
		{1288490188, "1.2 GiB"},
		{13636521574, "12.7 GiB"},
		{1 << 40, "1.0 TiB"},
		{1<<63 - 1, "8.0 EiB"},
	}
	for _, tt := range tests {
		t.Run(tt.want, func(t *testing.T) {
			if got := HumanSize(tt.in); got != tt.want {
				t.Errorf("HumanSize(%d) = %q, want %q", tt.in, got, tt.want)
			}
		})
	}
}

func TestTableNoColor(t *testing.T) {
	var b bytes.Buffer
	err := NewStyles(false).Table(&b, []string{"NAME", "SIZE", "MODIFIED"}, [][]string{
		{"sample-01.bam.c4gh", "1.2 GiB", "2026-10-28 14:02"},
		{"run-7.fastq.gz.c4gh", "12.7 GiB", "2026-10-29 09:15"},
	})
	if err != nil {
		t.Fatal(err)
	}
	const want = "NAME                  SIZE       MODIFIED\n" +
		"sample-01.bam.c4gh    1.2 GiB    2026-10-28 14:02\n" +
		"run-7.fastq.gz.c4gh   12.7 GiB   2026-10-29 09:15\n"
	if b.String() != want {
		t.Errorf("table =\n%q\nwant\n%q", b.String(), want)
	}
}

func TestTableColor(t *testing.T) {
	var b bytes.Buffer
	if err := NewStyles(true).Table(&b, []string{"NAME"}, [][]string{{"a"}}); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(b.String(), "\x1b[1mNAME") {
		t.Errorf("header is not bold: %q", b.String())
	}
}

func TestJSON(t *testing.T) {
	var b bytes.Buffer
	if err := JSON(&b, []map[string]any{{"name": "a&b.c4gh", "size": 1}}); err != nil {
		t.Fatal(err)
	}
	if want := `[{"name":"a&b.c4gh","size":1}]` + "\n"; b.String() != want {
		t.Errorf("JSON = %q, want %q", b.String(), want)
	}
}

func TestProgressOffTTY(t *testing.T) {
	tests := []struct {
		name       string
		size, from int64
		adds       []int64
		result     string
		want       string
	}{
		{
			name: "fresh upload", size: 3 << 20, adds: []int64{1 << 20, 1 << 20, 1 << 20}, result: "uploaded",
			want: "started  a.c4gh  3.0 MiB\nuploaded  a.c4gh\n",
		},
		{
			name: "resumed upload", size: 4 << 20, from: 1 << 20, adds: []int64{3 << 20}, result: "resumed",
			want: "started  a.c4gh  4.0 MiB, from 1.0 MiB\nresumed  a.c4gh\n",
		},
		{
			name: "failure", size: 10, adds: []int64{3}, result: "failed",
			want: "started  a.c4gh  10 B\nfailed  a.c4gh\n",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var b bytes.Buffer
			p := NewProgress(&b, false)
			p.Start("a.c4gh", tt.size, tt.from)
			for _, n := range tt.adds {
				p.Add(n)
			}
			p.End(tt.result)
			if b.String() != tt.want {
				t.Errorf("output = %q, want %q", b.String(), tt.want)
			}
			if strings.ContainsAny(b.String(), "\r\x1b") {
				t.Error("off a terminal the output has control characters")
			}
		})
	}
}

func TestProgressTTY(t *testing.T) {
	var b bytes.Buffer
	p := NewProgress(&b, true)
	p.Start("a.c4gh", 1000, 0)
	for range 1000 {
		p.Add(1)
	}
	p.End("uploaded")
	out := b.String()
	if got := strings.Count(out, "\r"); got != 102 {
		t.Errorf("redraws = %d, want one per percent plus start and end", got)
	}
	if !strings.HasSuffix(out, "\r\x1b[Kuploaded  a.c4gh\n") {
		t.Errorf("output ends %q", out[len(out)-40:])
	}
}

func TestBarLine(t *testing.T) {
	tests := []struct {
		name       string
		done, size int64
		want       string
	}{
		{name: "empty", done: 0, size: 1024, want: "f  [                              ]   0%  0 B / 1.0 KiB"},
		{name: "half", done: 512, size: 1024, want: "f  [===============               ]  50%  512 B / 1.0 KiB"},
		{name: "full", done: 1024, size: 1024, want: "f  [==============================] 100%  1.0 KiB / 1.0 KiB"},
		{name: "empty file", done: 0, size: 0, want: "f  [==============================] 100%  0 B / 0 B"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := barLine("f", tt.done, tt.size); got != tt.want {
				t.Errorf("barLine = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestMessages(t *testing.T) {
	var b bytes.Buffer
	Errorf(&b, "upload %s failed", "a.c4gh")
	Warnf(&b, "TLS verification is off")
	want := "lega: upload a.c4gh failed\nlega: warning: TLS verification is off\n"
	if b.String() != want {
		t.Errorf("output = %q, want %q", b.String(), want)
	}
}
