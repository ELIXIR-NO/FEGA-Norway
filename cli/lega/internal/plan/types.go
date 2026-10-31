package plan

import "time"

// LocalFile is a file found on disk for upload. Name is the base name it
// gets in the flat inbox; Crypt4GH reports whether its header parsed.
type LocalFile struct {
	Path, Name string
	Size       int64
	Crypt4GH   bool
}

// RemoteFile is one entry of an inbox or outbox listing.
type RemoteFile struct {
	Name     string
	Size     int64
	Modified time.Time
}

// Resumable is an interrupted upload as the backend reports it.
// NextOffset is the number of bytes already received, and LastChunkMD5 is
// the md5 of the bytes from PreviousOffset to NextOffset, the last chunk
// merged.
type Resumable struct {
	ID, Name                             string
	PreviousOffset, NextOffset, MaxChunk int64
	LastChunkMD5                         string
}

// Action is what a step does with its file.
type Action int

// The actions a plan can choose. Fetch is used by download steps only.
const (
	Upload Action = iota
	Resume
	Skip
	Reject
	Fetch
)

// Step is the decision for one local file in an upload.
type Step struct {
	File   LocalFile
	Action Action
	From   *Resumable // set for Resume
	Reason string     // set for Skip and Reject
}

// Chunk is one slice of a file to send: its 1-based chunk number, where it
// starts, and how many bytes it holds.
type Chunk struct {
	Num, Offset, Len int64
}

// UploadOptions are the upload flags that change the plan.
type UploadOptions struct {
	Force    bool
	NoResume bool
}

// DownloadStep is the decision for one outbox file in a download. Dir is
// the directory the file is written to, and Size is the size the listing
// reports, which the download is checked against.
type DownloadStep struct {
	Name   string
	Dir    string
	Action Action
	Reason string // set for Skip and Reject
	Size   int64
}
