package ui

import "os"

// IsTTY reports whether f is a terminal. A nil file, or one that cannot be
// inspected, is not.
func IsTTY(f *os.File) bool {
	if f == nil {
		return false
	}
	info, err := f.Stat()
	return err == nil && info.Mode()&os.ModeCharDevice != 0
}

// NoColor reports whether the user turned colour off: NO_COLOR set to
// anything non-empty, as no-color.org defines it.
func NoColor(getenv func(string) string) bool {
	return getenv("NO_COLOR") != ""
}
