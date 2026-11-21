package ui

import (
	"fmt"
	"io"
	"strings"
)

// barWidth is the number of cells in the progress bar.
const barWidth = 30

// Progress shows one file's transfer at a time. On a terminal it redraws a
// single line as bytes arrive; anywhere else it writes one line when a file
// starts and one when it ends, so a log stays readable.
type Progress struct {
	w       io.Writer
	tty     bool
	name    string
	size    int64
	done    int64
	percent int
}

// NewProgress returns a Progress writing to w, normally stderr. tty selects
// the redrawn line.
func NewProgress(w io.Writer, tty bool) *Progress {
	return &Progress{w: w, tty: tty}
}

// Start begins a file of size bytes, done of which were sent before, as on
// a resumed upload.
func (p *Progress) Start(name string, size, done int64) {
	p.name, p.size, p.done, p.percent = name, size, done, percentOf(done, size)
	if p.tty {
		p.redraw()
		return
	}
	fmt.Fprintln(p.w, startLine(name, size, done))
}

// Add counts n more bytes of the current file. A terminal line is redrawn
// only when the whole percentage changes, so small reads cost nothing.
func (p *Progress) Add(n int64) {
	p.done += n
	if percent := percentOf(p.done, p.size); p.tty && percent != p.percent {
		p.percent = percent
		p.redraw()
	}
}

// End finishes the current file with a one-word result such as "uploaded"
// or "failed". On a terminal the bar is replaced by the result line.
func (p *Progress) End(result string) {
	if p.tty {
		fmt.Fprint(p.w, "\r\x1b[K")
	}
	fmt.Fprintf(p.w, "%s  %s\n", result, p.name)
}

func (p *Progress) redraw() {
	fmt.Fprint(p.w, "\r"+barLine(p.name, p.done, p.size)+"\x1b[K")
}

func startLine(name string, size, done int64) string {
	if done > 0 {
		return fmt.Sprintf("started  %s  %s, from %s", name, HumanSize(size), HumanSize(done))
	}
	return fmt.Sprintf("started  %s  %s", name, HumanSize(size))
}

// barLine renders the terminal line for done of size bytes.
func barLine(name string, done, size int64) string {
	percent := percentOf(done, size)
	filled := percent * barWidth / 100
	bar := strings.Repeat("=", filled) + strings.Repeat(" ", barWidth-filled)
	return fmt.Sprintf("%s  [%s] %3d%%  %s / %s", name, bar, percent, HumanSize(done), HumanSize(size))
}

// percentOf is the whole percentage of size that done is, between 0 and
// 100. An empty file counts as complete.
func percentOf(done, size int64) int {
	switch {
	case size <= 0:
		return 100
	case done >= size:
		return 100
	case done <= 0:
		return 0
	default:
		return int(done * 100 / size)
	}
}
