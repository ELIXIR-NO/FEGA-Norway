package ui

import (
	"fmt"
	"io"
)

// Errorf writes one error line, prefixed "lega: ", to w.
func Errorf(w io.Writer, format string, a ...any) {
	fmt.Fprintf(w, "lega: "+format+"\n", a...)
}

// Warnf writes one warning line, prefixed "lega: warning: ", to w.
func Warnf(w io.Writer, format string, a ...any) {
	fmt.Fprintf(w, "lega: warning: "+format+"\n", a...)
}

// Infof writes one plain line to w, with no prefix.
func Infof(w io.Writer, format string, a ...any) error {
	_, err := fmt.Fprintf(w, format+"\n", a...)
	return err
}
