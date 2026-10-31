package ui

import (
	"io"

	"github.com/charmbracelet/lipgloss"
	"github.com/muesli/termenv"
)

// Styles are the text styles lega uses. Built without colour they render
// plain text with no escape codes at all, which is what a pipe or a CI log
// needs.
type Styles struct {
	Header lipgloss.Style
	OK     lipgloss.Style
	Error  lipgloss.Style
	Faint  lipgloss.Style
	plain  lipgloss.Style
}

// NewStyles returns the styles for a colour terminal, or plain ones when
// color is false. The profile is set here instead of detected by lipgloss,
// so the caller's TTY and NO_COLOR decision is the only one.
func NewStyles(color bool) Styles {
	r := lipgloss.NewRenderer(io.Discard)
	r.SetColorProfile(profile(color))
	return Styles{
		Header: r.NewStyle().Bold(true),
		OK:     r.NewStyle().Foreground(lipgloss.Color("2")),
		Error:  r.NewStyle().Foreground(lipgloss.Color("1")),
		Faint:  r.NewStyle().Faint(true),
		plain:  r.NewStyle(),
	}
}

func profile(color bool) termenv.Profile {
	if color {
		return termenv.ANSI
	}
	return termenv.Ascii
}
