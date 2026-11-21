package plan

import (
	"fmt"
	"strings"
)

// PlanDownload decides, for each requested name, whether to fetch it, skip
// it or reject it. With no names it plans the whole outbox, in listing
// order. A name given twice is planned once, since both would write the
// same file. local maps the base names of files already in dir to their
// size and modification time. The rules run per name and the first match
// wins.
func PlanDownload(names []string, outbox []RemoteFile, local map[string]LocalCopy, dir string) []DownloadStep {
	if len(names) == 0 {
		names = fileNames(outbox)
	}
	names = unique(names)
	x := downloadIndex{outbox: remoteFiles(outbox), local: local, dir: dir}
	steps := make([]DownloadStep, len(names))
	for i, name := range names {
		steps[i] = x.step(name)
	}
	return steps
}

// downloadIndex holds the lookups every rule needs, built once per plan.
type downloadIndex struct {
	outbox map[string]RemoteFile
	local  map[string]LocalCopy
	dir    string
}

type downloadRule func(x downloadIndex, name string) (DownloadStep, bool)

var downloadRules = []downloadRule{
	rejectNotInOutbox,
	rejectUnsafeName,
	skipOrRejectExisting,
}

func (x downloadIndex) step(name string) DownloadStep {
	for _, rule := range downloadRules {
		if s, ok := rule(x, name); ok {
			return s
		}
	}
	return x.result(name, Fetch, "")
}

func rejectNotInOutbox(x downloadIndex, name string) (DownloadStep, bool) {
	if _, ok := x.outbox[name]; ok {
		return DownloadStep{}, false
	}
	return x.result(name, Reject, "not in the outbox"), true
}

// rejectUnsafeName keeps a bad listing from writing outside dir: the name
// comes from the server and becomes a local path.
func rejectUnsafeName(x downloadIndex, name string) (DownloadStep, bool) {
	if isCleanName(name) {
		return DownloadStep{}, false
	}
	return x.result(name, Reject, "not a plain file name, so it cannot be saved safely"), true
}

// skipOrRejectExisting treats a same-size file as a finished download,
// unless the outbox copy was exported after it: sda-doa overwrites a
// re-exported file in place, and a re-encryption keeps the size. A file of
// another size is likely partial. Neither is overwritten.
func skipOrRejectExisting(x downloadIndex, name string) (DownloadStep, bool) {
	have, ok := x.local[name]
	if !ok {
		return DownloadStep{}, false
	}
	want := x.outbox[name]
	switch {
	case have.Size != want.Size:
		return x.result(name, Reject, fmt.Sprintf("a local file of %d bytes has this name but the outbox file has %d; remove it to download again", have.Size, want.Size)), true
	case want.Modified.After(have.Modified):
		return x.result(name, Reject, fmt.Sprintf("the outbox copy was exported %s, after the local file (%s); remove the local file to download it again", want.Modified.Format(timeLayout), have.Modified.Format(timeLayout))), true
	default:
		return x.result(name, Skip, "already downloaded"), true
	}
}

const timeLayout = "2006-01-02 15:04"

func (x downloadIndex) result(name string, a Action, reason string) DownloadStep {
	f := x.outbox[name]
	return DownloadStep{Name: name, Dir: x.dir, Action: a, Reason: reason, Size: f.Size, Modified: f.Modified}
}

// isCleanName reports whether name is a single path element that is safe to
// join to a directory: not empty, not . or .., and free of separators and
// NUL bytes on every platform.
func isCleanName(name string) bool {
	return name != "" && name != "." && name != ".." && !strings.ContainsAny(name, "/\\\x00")
}

func fileNames(files []RemoteFile) []string {
	names := make([]string, len(files))
	for i, f := range files {
		names[i] = f.Name
	}
	return names
}

func remoteFiles(files []RemoteFile) map[string]RemoteFile {
	m := make(map[string]RemoteFile, len(files))
	for _, f := range files {
		m[f.Name] = f
	}
	return m
}

// unique drops every repeat of a name, keeping the first.
func unique(names []string) []string {
	seen := make(map[string]bool, len(names))
	out := make([]string, 0, len(names))
	for _, n := range names {
		if !seen[n] {
			seen[n] = true
			out = append(out, n)
		}
	}
	return out
}
