package ui

import (
	"fmt"
	"io"
	"time"
)

// modifiedLayout shows a listing's modification time to the minute.
const modifiedLayout = "2006-01-02 15:04"

// File is one row of an inbox or outbox listing. Its JSON form carries the
// size in bytes and the time in RFC 3339.
type File struct {
	Name     string    `json:"name"`
	Size     int64     `json:"size"`
	Modified time.Time `json:"modified,omitzero"`
}

// Page says which page a listing shows. The zero Page is a full listing.
type Page struct {
	Number, PerPage int
}

// Files writes files as a table followed by a count line, or empty instead
// when there are none.
func (s Styles) Files(w io.Writer, files []File, page Page, empty string) error {
	if len(files) == 0 {
		return writeLine(w, empty)
	}
	rows := make([][]string, len(files))
	for i, f := range files {
		rows[i] = []string{f.Name, HumanSize(f.Size), formatModified(f.Modified)}
	}
	if err := s.Table(w, []string{"NAME", "SIZE", "MODIFIED"}, rows); err != nil {
		return err
	}
	return writeLine(w, "\n"+filesFooter(files, page))
}

// filesFooter counts the files and their bytes, and on a single page says
// how to get the rest.
func filesFooter(files []File, page Page) string {
	var total int64
	for _, f := range files {
		total += f.Size
	}
	footer := fmt.Sprintf("%s, %s", Count(len(files), "file"), HumanSize(total))
	if page.PerPage == 0 {
		return footer
	}
	return fmt.Sprintf("%s (page %d, %d per page; --all for everything)", footer, page.Number, page.PerPage)
}

func formatModified(t time.Time) string {
	if t.IsZero() {
		return ""
	}
	return t.Format(modifiedLayout)
}

// Resumable is one row of the interrupted-uploads listing. Received is the
// number of bytes the backend holds, not the size of the file.
type Resumable struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Received int64  `json:"received"`
}

// Resumables writes the interrupted uploads as a table, or empty instead
// when there are none.
func (s Styles) Resumables(w io.Writer, rs []Resumable, empty string) error {
	if len(rs) == 0 {
		return writeLine(w, empty)
	}
	rows := make([][]string, len(rs))
	for i, r := range rs {
		rows[i] = []string{r.ID, r.Name, HumanSize(r.Received)}
	}
	return s.Table(w, []string{"ID", "NAME", "RECEIVED"}, rows)
}

// Count writes n with noun, made plural by an s unless n is one.
func Count(n int, noun string) string {
	if n == 1 {
		return fmt.Sprintf("1 %s", noun)
	}
	return fmt.Sprintf("%d %ss", n, noun)
}

func writeLine(w io.Writer, line string) error {
	_, err := fmt.Fprintln(w, line)
	return err
}
