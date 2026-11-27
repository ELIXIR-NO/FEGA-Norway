package transfer

import (
	"context"
	"io"
	"io/fs"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// Uploader is a backend an upload runs against: the proxy, or TSD directly.
// PutChunk returns the upload ID the backend assigns; an empty uploadID
// starts a new upload. Resumable looks an upload up by ID, or by name when
// uploadID is empty, and reports ok only for exactly one match.
type Uploader interface {
	PutChunk(ctx context.Context, fileName, uploadID string, chunk plan.Chunk, data []byte) (string, error)
	Finish(ctx context.Context, fileName, uploadID string, size int64, sha256 string) error
	Resumable(ctx context.Context, fileName, uploadID string) (plan.Resumable, bool, error)
}

// Downloader opens one outbox file. The length is -1 when the backend does
// not declare one; the caller closes the body.
type Downloader interface {
	Download(ctx context.Context, fileName string) (io.ReadCloser, int64, error)
}

// File is a local file opened for upload. *os.File satisfies it.
type File interface {
	io.ReaderAt
	io.Closer
	Stat() (fs.FileInfo, error)
}

// Outcome is what happened to one file of a batch.
type Outcome int

// The outcomes a file can end in. Downloaded is used by downloads only.
const (
	Uploaded Outcome = iota
	Resumed
	Downloaded
	Skipped
	Failed
	Rejected
)

var outcomeWords = map[Outcome]string{
	Uploaded:   "uploaded",
	Resumed:    "resumed",
	Downloaded: "downloaded",
	Skipped:    "skipped",
	Failed:     "failed",
	Rejected:   "rejected",
}

// String returns the outcome as the one word a summary line starts with.
func (o Outcome) String() string { return outcomeWords[o] }

// Result is the outcome of one file. From is the number of bytes the backend
// already held when a resumed upload started; Reason explains a skip or a
// rejection, and Err a failure.
type Result struct {
	Name    string
	Path    string
	Outcome Outcome
	Size    int64
	From    int64
	Reason  string
	Err     error
}

// Report holds one Result per step, in the order of the steps.
type Report struct {
	Results []Result
}

// Count returns how many files ended in any of the given outcomes.
func (r Report) Count(outcomes ...Outcome) int {
	n := 0
	for _, res := range r.Results {
		for _, o := range outcomes {
			if res.Outcome == o {
				n++
			}
		}
	}
	return n
}

// Done counts the files that need nothing more; a skipped file counts.
func (r Report) Done() int { return r.Count(Uploaded, Resumed, Downloaded, Skipped) }

// Failed counts the files that did not get where they were going.
func (r Report) Failed() int { return r.Count(Failed, Rejected) }

// EventKind says what an Event reports.
type EventKind int

// The events a run reports through Runner.Progress.
const (
	FileStarted EventKind = iota
	Bytes
	FileDone
	FileSkipped
	FileFailed
	Warning
)

// Event is one step of a run's progress. Size and Done are set on
// FileStarted, Done being the bytes the backend already held; N is set on
// Bytes; Outcome on FileDone; Message on FileSkipped and Warning; Err on
// FileFailed.
type Event struct {
	Kind    EventKind
	Name    string
	Size    int64
	Done    int64
	N       int64
	Outcome Outcome
	Message string
	Err     error
}
