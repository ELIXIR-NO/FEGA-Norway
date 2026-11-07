package ui

import (
	"encoding/json"
	"fmt"
	"io"
	"strings"

	"github.com/charmbracelet/lipgloss"
	"github.com/charmbracelet/lipgloss/table"
)

// columnGap is the space between table columns.
const columnGap = 3

// Table writes rows under a bold header, with no borders and columns
// separated by spaces.
func (s Styles) Table(w io.Writer, headers []string, rows [][]string) error {
	last := len(headers) - 1
	t := table.New().
		BorderTop(false).BorderBottom(false).BorderLeft(false).BorderRight(false).
		BorderHeader(false).BorderColumn(false).BorderRow(false).
		Headers(headers...).
		Rows(rows...).
		StyleFunc(func(row, col int) lipgloss.Style {
			return cellStyle(s, row, col, last)
		})
	_, err := io.WriteString(w, trimLines(t.Render())+"\n")
	return err
}

func cellStyle(s Styles, row, col, last int) lipgloss.Style {
	style := s.plain
	if row == table.HeaderRow {
		style = s.Header
	}
	if col < last {
		style = style.PaddingRight(columnGap)
	}
	return style
}

// trimLines drops the padding lipgloss puts after the last column.
func trimLines(s string) string {
	lines := strings.Split(s, "\n")
	for i, l := range lines {
		lines[i] = strings.TrimRight(l, " ")
	}
	return strings.Join(lines, "\n")
}

// JSON writes v as one line of JSON. Names are written as they are, so an
// ampersand in a file name stays an ampersand.
func JSON(w io.Writer, v any) error {
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	return enc.Encode(v)
}

// HumanSize formats a byte count in binary units with one decimal, as
// 980.4 MiB; counts under 1 KiB stay in bytes.
func HumanSize(n int64) string {
	const unit = 1024
	if n < unit {
		return fmt.Sprintf("%d B", n)
	}
	value, exp := float64(n)/unit, 0
	// Moving up below 1024 keeps 1023.99 KiB from printing as 1024.0 KiB.
	for value >= unit-0.05 && exp < len(binaryPrefixes)-1 {
		value /= unit
		exp++
	}
	return fmt.Sprintf("%.1f %siB", value, binaryPrefixes[exp])
}

var binaryPrefixes = []string{"K", "M", "G", "T", "P", "E"}
