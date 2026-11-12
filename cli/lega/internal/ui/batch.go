package ui

import (
	"fmt"
	"io"
	"strings"

	"github.com/charmbracelet/lipgloss"
)

// Line is one file's result in a batch summary: an outcome word such as
// "uploaded", the file's name, and a detail such as its size or what went
// wrong.
type Line struct {
	Outcome string `json:"outcome"`
	Name    string `json:"name"`
	Detail  string `json:"detail,omitempty"`
}

// Tally is one number of a batch's closing line, as in "2 uploaded".
type Tally struct {
	Word string
	N    int
}

// Batch is the summary of a command that worked through several files.
type Batch struct {
	Lines   []Line
	Tallies []Tally
}

// Batch writes one aligned line per file, a blank line, and the tallies,
// as in "2 uploaded, 1 skipped, 0 failed".
func (s Styles) Batch(w io.Writer, b Batch) error {
	_, err := io.WriteString(w, s.lines(b.Lines)+"\n"+tallies(b.Tallies)+"\n")
	return err
}

// BatchJSON writes b as one JSON object: the lines under "files", and each
// tally under its word.
func BatchJSON(w io.Writer, b Batch) error {
	out := map[string]any{"files": b.Lines}
	if b.Lines == nil {
		out["files"] = []Line{}
	}
	for _, t := range b.Tallies {
		out[t.Word] = t.N
	}
	return JSON(w, out)
}

func (s Styles) lines(lines []Line) string {
	rows := make([][]string, len(lines))
	for i, l := range lines {
		rows[i] = []string{l.Outcome, l.Name, l.Detail}
	}
	var b strings.Builder
	for i, cells := range padColumns(rows) {
		line := s.outcomeStyle(lines[i].Outcome).Render(cells[0]) + strings.Join(cells[1:], "")
		b.WriteString(strings.TrimRight(line, " ") + "\n")
	}
	return b.String()
}

// outcomeStyle colours the outcome word; the rest of a line stays plain.
func (s Styles) outcomeStyle(outcome string) lipgloss.Style {
	switch outcome {
	case "failed", "rejected":
		return s.Error
	case "skipped":
		return s.Faint
	default:
		return s.OK
	}
}

func tallies(ts []Tally) string {
	parts := make([]string, len(ts))
	for i, t := range ts {
		parts[i] = fmt.Sprintf("%d %s", t.N, t.Word)
	}
	return strings.Join(parts, ", ")
}

// Indented renders rows as aligned columns, each line indented by two
// spaces, for a list inside a message.
func Indented(rows [][]string) string {
	var b strings.Builder
	for _, cells := range padColumns(rows) {
		b.WriteString("\n  " + strings.TrimRight(strings.Join(cells, ""), " "))
	}
	return b.String()
}

// padColumns pads every cell but the last of its row to its column's width
// plus the column gap.
func padColumns(rows [][]string) [][]string {
	widths := columnWidths(rows)
	out := make([][]string, len(rows))
	for i, row := range rows {
		out[i] = make([]string, len(row))
		for j, cell := range row {
			out[i][j] = cell
			if j < len(row)-1 {
				out[i][j] += strings.Repeat(" ", widths[j]-lipgloss.Width(cell)+columnGap)
			}
		}
	}
	return out
}

func columnWidths(rows [][]string) []int {
	var widths []int
	for _, row := range rows {
		for j, cell := range row {
			if j == len(widths) {
				widths = append(widths, 0)
			}
			widths[j] = max(widths[j], lipgloss.Width(cell))
		}
	}
	return widths
}
