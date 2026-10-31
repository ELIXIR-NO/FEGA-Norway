package transfer

import (
	"context"
	"crypto/md5" // #nosec G501 -- matches TSD's per-chunk md5, not used for security
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"hash"
	"io"
	"os"
	"strings"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// Runner executes upload and download plans. ChunkSize is in bytes; Retries
// is how many times a failed request is tried again, 0 for never; Progress,
// if set, receives every Event. Open defaults to os.Open and Sleep to a
// timer that honours the context, so tests can run retries instantly.
type Runner struct {
	Open      func(path string) (File, error)
	ChunkSize int64
	Retries   int
	Progress  func(Event)
	Sleep     func(ctx context.Context, d time.Duration) error
}

// Upload runs every step against up, one file at a time, and returns one
// Result per step. A failed file does not stop the others, but a
// cancelled context fails every file not yet finished.
func (r Runner) Upload(ctx context.Context, up Uploader, steps []plan.Step) Report {
	var rep Report
	for _, s := range steps {
		rep.Results = append(rep.Results, r.uploadStep(ctx, up, s))
	}
	return rep
}

func (r Runner) uploadStep(ctx context.Context, up Uploader, s plan.Step) Result {
	switch s.Action {
	case plan.Skip:
		r.emit(Event{Kind: FileSkipped, Name: s.File.Name, Message: s.Reason})
		return stepResult(s, Skipped, nil)
	case plan.Reject:
		return stepResult(s, Rejected, nil)
	case plan.Upload, plan.Resume:
		return r.reportUpload(s, r.uploadFile(ctx, up, s))
	default:
		return stepResult(s, Failed, fmt.Errorf("%s: no upload action", s.File.Path))
	}
}

func (r Runner) reportUpload(s plan.Step, err error) Result {
	if err != nil {
		r.emit(Event{Kind: FileFailed, Name: s.File.Name, Err: err})
		return stepResult(s, Failed, err)
	}
	outcome := Uploaded
	if s.Action == plan.Resume {
		outcome = Resumed
	}
	r.emit(Event{Kind: FileDone, Name: s.File.Name, Outcome: outcome})
	return stepResult(s, outcome, nil)
}

func stepResult(s plan.Step, o Outcome, err error) Result {
	res := Result{Name: s.File.Name, Path: s.File.Path, Outcome: o, Size: s.File.Size, Err: err}
	if o == Skipped || o == Rejected {
		res.Reason = s.Reason
	}
	if s.Action == plan.Resume && s.From != nil {
		res.From = s.From.NextOffset
	}
	return res
}

// uploadFile sends one file: it reopens it, checks it is still the file
// that was scanned, picks up where a resumable left off, sends the rest in
// chunks and finishes with the whole file's size and sha256.
func (r Runner) uploadFile(ctx context.Context, up Uploader, s plan.Step) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	offset := resumeOffset(s)
	r.emit(Event{Kind: FileStarted, Name: s.File.Name, Size: s.File.Size, Done: offset})
	f, err := r.openChecked(s.File)
	if err != nil {
		return err
	}
	defer f.Close()
	u := &fileUpload{runner: r, up: up, file: f, local: s.File, sha: sha256.New()}
	if err := u.resume(s.From); err != nil {
		return err
	}
	if err := u.sendChunks(ctx, plan.Chunks(s.File.Size, offset, r.ChunkSize, firstChunk(s.From))); err != nil {
		return err
	}
	return u.finish(ctx)
}

func resumeOffset(s plan.Step) int64 {
	if s.Action == plan.Resume && s.From != nil {
		return s.From.NextOffset
	}
	return 0
}

func firstChunk(from *plan.Resumable) int64 {
	if from == nil {
		return 1
	}
	return from.MaxChunk + 1
}

func (r Runner) openChecked(local plan.LocalFile) (File, error) {
	open := r.Open
	if open == nil {
		open = func(path string) (File, error) { return os.Open(path) }
	}
	f, err := open(local.Path)
	if err != nil {
		return nil, err
	}
	if err := checkSize(f, local); err != nil {
		f.Close()
		return nil, err
	}
	return f, nil
}

// checkSize refuses a file whose size changed since the scan: the plan and
// any resumable it matched were decided on the old size.
func checkSize(f File, local plan.LocalFile) error {
	info, err := f.Stat()
	if err != nil {
		return err
	}
	if info.Size() != local.Size {
		return fmt.Errorf("%s changed while uploading: it was %d bytes and is now %d", local.Path, local.Size, info.Size())
	}
	return nil
}

func (r Runner) emit(e Event) {
	if r.Progress != nil {
		r.Progress(e)
	}
}

// fileUpload is the state of one file's upload: the upload ID once the
// backend has assigned one, and the sha256 of every byte the backend holds.
type fileUpload struct {
	runner   Runner
	up       Uploader
	file     File
	local    plan.LocalFile
	uploadID string
	sha      hash.Hash
	buf      []byte
}

// resume adopts an interrupted upload after checking that its last chunk
// came from this file, and hashes the bytes it already holds so that the
// final sha256 covers the whole file.
func (u *fileUpload) resume(from *plan.Resumable) error {
	if from == nil {
		return nil
	}
	if err := u.verifyResumable(*from); err != nil {
		return err
	}
	u.uploadID = from.ID
	return hashRange(u.sha, u.file, 0, from.NextOffset)
}

// verifyResumable closes the gap left by matching a resumable on its name
// alone: a different file with the same name fails here. Without the md5
// from the backend only the size guard in the plan protects the upload.
func (u *fileUpload) verifyResumable(from plan.Resumable) error {
	if from.LastChunkMD5 == "" {
		u.runner.emit(Event{Kind: Warning, Name: u.local.Name, Message: "the interrupted upload has no checksum to compare, so only its size was checked"})
		return nil
	}
	sum, err := rangeMD5(u.file, from.PreviousOffset, from.NextOffset)
	if err != nil {
		return err
	}
	if !strings.EqualFold(sum, from.LastChunkMD5) {
		return fmt.Errorf("%s does not match interrupted upload %s; remove it with lega resumables rm %s, or upload with --no-resume", u.local.Path, from.ID, from.ID)
	}
	return nil
}

func rangeMD5(f io.ReaderAt, from, to int64) (string, error) {
	h := md5.New() // #nosec G401
	if err := hashRange(h, f, from, to); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// hashRange writes the bytes [from, to) of f into h.
func hashRange(h io.Writer, f io.ReaderAt, from, to int64) error {
	if from < 0 || to < from {
		return fmt.Errorf("byte range %d to %d is not valid", from, to)
	}
	n, err := io.Copy(h, io.NewSectionReader(f, from, to-from))
	if err == nil && n != to-from {
		err = io.ErrUnexpectedEOF
	}
	return err
}

func (u *fileUpload) sendChunks(ctx context.Context, chunks []plan.Chunk) error {
	for _, c := range chunks {
		data, err := u.read(c)
		if err != nil {
			return err
		}
		if err := u.sendChunk(ctx, c, data); err != nil {
			return err
		}
		u.sha.Write(data)
		u.runner.emit(Event{Kind: Bytes, Name: u.local.Name, N: c.Len})
	}
	return nil
}

// read fills one buffer, reused across chunks, with the bytes of c.
func (u *fileUpload) read(c plan.Chunk) ([]byte, error) {
	if int64(cap(u.buf)) < c.Len {
		u.buf = make([]byte, c.Len)
	}
	data := u.buf[:c.Len]
	n, err := u.file.ReadAt(data, c.Offset)
	if int64(n) == c.Len {
		return data, nil
	}
	if err == nil || errors.Is(err, io.EOF) {
		err = io.ErrUnexpectedEOF
	}
	return nil, fmt.Errorf("read %s at byte %d: %w", u.local.Path, c.Offset, err)
}

// finish checks the size once more, since the file could have grown or
// shrunk while its chunks went out, and asks the backend to assemble it.
func (u *fileUpload) finish(ctx context.Context) error {
	if err := checkSize(u.file, u.local); err != nil {
		return err
	}
	if u.uploadID == "" {
		return fmt.Errorf("%s: no chunk was sent, so there is nothing to finish", u.local.Path)
	}
	return u.up.Finish(ctx, u.local.Name, u.uploadID, u.local.Size, hex.EncodeToString(u.sha.Sum(nil)))
}
