// Package report renders human-readable e2e test output: stage banners with
// per-stage timing, pretty-printed JSON, aligned check marks, and a pass/fail
// summary. It replaces slog's default key=value text output while staying
// slog-compatible: Reporter embeds a *slog.Logger backed by a custom handler,
// so existing `log.Info(msg, "k", v)` calls keep working and render as clean
// "    · msg  k=v" lines.
//
// Colour (ANSI) is emitted only when the writer is a terminal, so `docker logs`
// and piped/captured output stay plain.
package report

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"os"
	"regexp"
	"strings"
	"time"
)

const barWidth = 62

// ANSI codes; blanked out when colour is disabled.
const (
	cReset  = "\x1b[0m"
	cBold   = "\x1b[1m"
	cDim    = "\x1b[2m"
	cRed    = "\x1b[31m"
	cGreen  = "\x1b[32m"
	cYellow = "\x1b[33m"
	cCyan   = "\x1b[36m"
)

// Reporter renders test output and doubles as the slog.Logger threaded through
// the pipeline (via the embedded *slog.Logger).
type Reporter struct {
	*slog.Logger
	w     io.Writer
	color bool

	curN, curTotal int
	curName        string
}

// New builds a Reporter writing to w. Colour is on only when w is a terminal.
func New(w io.Writer) *Reporter {
	color := isTerminal(w)
	r := &Reporter{w: w, color: color}
	r.Logger = slog.New(&handler{w: w, color: color})
	return r
}

func (r *Reporter) paint(color, s string) string {
	if !r.color || color == "" {
		return s
	}
	return color + s + cReset
}

// Banner prints the top-of-run title block.
func (r *Reporter) Banner(title, sub string) {
	bar := strings.Repeat("═", barWidth)
	head := "  " + title
	if sub != "" {
		head += "  ·  " + sub
	}
	fmt.Fprintf(r.w, "\n%s\n%s\n%s\n", r.paint(cCyan, bar), r.paint(cBold, head), r.paint(cCyan, bar))
}

// Stage announces the start of a stage and records it for the matching Pass/Fail.
func (r *Reporter) Stage(n, total int, name string) {
	r.curN, r.curTotal, r.curName = n, total, name
	fmt.Fprintf(r.w, "\n%s %s %s\n",
		r.paint(cCyan, "▶"),
		r.paint(cDim, fmt.Sprintf("[%d/%d]", n, total)),
		r.paint(cBold, name))
}

// Pass closes the current stage as succeeded.
func (r *Reporter) Pass(d time.Duration) {
	fmt.Fprintf(r.w, "%s %s %s %s\n",
		r.paint(cGreen, "✓"),
		r.paint(cDim, fmt.Sprintf("[%d/%d]", r.curN, r.curTotal)),
		r.curName,
		r.paint(cDim, "("+fmtDur(d)+")"))
}

// Fail closes the current stage as failed and prints the error.
func (r *Reporter) Fail(d time.Duration, err error) {
	fmt.Fprintf(r.w, "%s %s %s %s\n",
		r.paint(cRed, "✗"),
		r.paint(cDim, fmt.Sprintf("[%d/%d]", r.curN, r.curTotal)),
		r.paint(cBold, r.curName),
		r.paint(cDim, "("+fmtDur(d)+")"))
	fmt.Fprintf(r.w, "    %s %s\n", r.paint(cRed, "✗"), err.Error())
}

// Summary prints the closing pass/fail block.
func (r *Reporter) Summary(passed bool, done, total int, d time.Duration) {
	bar := strings.Repeat("═", barWidth)
	tail := fmt.Sprintf("  ·  %d/%d stages  ·  %s", done, total, fmtDur(d))
	var line string
	if passed {
		line = r.paint(cGreen, "✓ PASSED") + r.paint(cDim, tail)
	} else {
		line = r.paint(cRed, "✗ FAILED") + r.paint(cDim, tail)
	}
	fmt.Fprintf(r.w, "\n%s\n  %s\n%s\n", r.paint(cCyan, bar), line, r.paint(cCyan, bar))
}

// JSON prints a labelled, pretty-printed JSON block. Falls back to the raw
// (trimmed) value if it does not parse.
func (r *Reporter) JSON(label string, raw []byte) {
	fmt.Fprintf(r.w, "    %s %s\n", r.paint(cDim, "·"), label)
	var buf bytes.Buffer
	if err := json.Indent(&buf, raw, "        ", "  "); err == nil {
		fmt.Fprintf(r.w, "        %s\n", buf.String())
		return
	}
	fmt.Fprintf(r.w, "        %s\n", strings.TrimSpace(string(raw)))
}

// Check prints an aligned, labelled assertion result.
func (r *Reporter) Check(label string, ok bool) {
	mark := r.paint(cGreen, "✓")
	if !ok {
		mark = r.paint(cRed, "✗")
	}
	fmt.Fprintf(r.w, "    %s %-30s %s\n", r.paint(cDim, "·"), label, mark)
}

// Block prints a labelled, indented multi-line block (e.g. sanitized subprocess
// output).
func (r *Reporter) Block(label string, lines []string) {
	fmt.Fprintf(r.w, "    %s %s\n", r.paint(cDim, "·"), label)
	for _, ln := range lines {
		fmt.Fprintf(r.w, "        %s\n", ln)
	}
}

// Wait notes a fixed inter-stage settle period.
func (r *Reporter) Wait(ms int) {
	d := time.Duration(ms) * time.Millisecond
	fmt.Fprintf(r.w, "    %s\n", r.paint(cDim, "· settling "+fmtDur(d)))
}

func fmtDur(d time.Duration) string {
	if d < time.Second {
		return fmt.Sprintf("%dms", d.Milliseconds())
	}
	return fmt.Sprintf("%.2fs", d.Seconds())
}

func isTerminal(w io.Writer) bool {
	f, ok := w.(*os.File)
	if !ok {
		return false
	}
	fi, err := f.Stat()
	if err != nil {
		return false
	}
	return fi.Mode()&os.ModeCharDevice != 0
}

var (
	ansiRE   = regexp.MustCompile(`\x1b\[[0-9;]*[a-zA-Z]`)
	legaTSRE = regexp.MustCompile(`^\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2} `)
)

// SanitizeLega turns lega-commander's raw combined output into the few lines
// worth reading: it strips ANSI colour, the leading log timestamps, the
// repeated progress-bar frames and the TLS-skip warnings, and de-duplicates.
func SanitizeLega(out []byte) []string {
	clean := ansiRE.ReplaceAllString(string(out), "")
	// The progress bar redraws with \r, so frames (and any status line printed
	// after them) share one \n-delimited line; split on \r too to separate them.
	clean = strings.ReplaceAll(clean, "\r", "\n")
	seen := make(map[string]bool)
	var keep []string
	for _, ln := range strings.Split(clean, "\n") {
		ln = legaTSRE.ReplaceAllString(strings.TrimSpace(ln), "")
		ln = strings.TrimSpace(ln)
		if ln == "" || strings.Contains(ln, "WARNING:") {
			continue
		}
		// Progress-bar frames: "0 / N [____] 12.00% 1234 p/s".
		if strings.Contains(ln, "p/s") || (strings.Contains(ln, "[") && strings.Contains(ln, "%")) {
			continue
		}
		if seen[ln] {
			continue
		}
		seen[ln] = true
		keep = append(keep, ln)
	}
	return keep
}

// handler is a minimal slog.Handler that renders records as clean
// "    · message  key=value" lines (no timestamp/level noise), with a ✗/⚠
// glyph for error/warn levels.
type handler struct {
	w     io.Writer
	color bool
	attrs []slog.Attr
}

func (h *handler) Enabled(_ context.Context, l slog.Level) bool { return l >= slog.LevelInfo }

func (h *handler) Handle(_ context.Context, rec slog.Record) error {
	glyph, col := "·", cDim
	switch {
	case rec.Level >= slog.LevelError:
		glyph, col = "✗", cRed
	case rec.Level >= slog.LevelWarn:
		glyph, col = "⚠", cYellow
	}

	var b strings.Builder
	b.WriteString("    ")
	b.WriteString(h.paint(col, glyph))
	b.WriteByte(' ')
	b.WriteString(rec.Message)
	for _, a := range h.attrs {
		h.writeAttr(&b, a)
	}
	rec.Attrs(func(a slog.Attr) bool {
		h.writeAttr(&b, a)
		return true
	})
	b.WriteByte('\n')
	_, err := io.WriteString(h.w, b.String())
	return err
}

func (h *handler) writeAttr(b *strings.Builder, a slog.Attr) {
	b.WriteByte(' ')
	b.WriteString(h.paint(cDim, a.Key+"="))
	b.WriteString(a.Value.String())
}

func (h *handler) WithAttrs(as []slog.Attr) slog.Handler {
	nh := *h
	nh.attrs = append(append([]slog.Attr(nil), h.attrs...), as...)
	return &nh
}

func (h *handler) WithGroup(string) slog.Handler { return h }

func (h *handler) paint(color, s string) string {
	if !h.color {
		return s
	}
	return color + s + cReset
}
