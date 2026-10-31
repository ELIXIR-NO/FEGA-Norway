package plan

import (
	"fmt"
	"strings"
)

// InInbox is the reason a file already in the inbox is skipped.
const InInbox = "already in the inbox"

// PlanUpload decides, for each local file, whether to upload it fresh,
// resume it, skip it or reject it. It returns one Step per file, in the
// order of local. The rules run per file and the first match wins.
func PlanUpload(local []LocalFile, inbox []RemoteFile, resumables []Resumable, o UploadOptions) []Step {
	x := uploadIndex{
		local:      groupLocal(local),
		inbox:      remoteNames(inbox),
		resumables: groupResumables(resumables),
		opts:       o,
	}
	steps := make([]Step, len(local))
	for i, f := range local {
		steps[i] = x.step(f)
	}
	return steps
}

// uploadIndex holds the lookups every rule needs, built once per plan.
type uploadIndex struct {
	local      map[string][]LocalFile
	inbox      map[string]bool
	resumables map[string][]Resumable
	opts       UploadOptions
}

type uploadRule func(x uploadIndex, f LocalFile) (Step, bool)

var uploadRules = []uploadRule{
	rejectNotCrypt4GH,
	rejectNameCollision,
	skipInInbox,
	resumeInterrupted,
}

func (x uploadIndex) step(f LocalFile) Step {
	for _, rule := range uploadRules {
		if s, ok := rule(x, f); ok {
			return s
		}
	}
	return Step{File: f, Action: Upload}
}

func rejectNotCrypt4GH(_ uploadIndex, f LocalFile) (Step, bool) {
	if f.Crypt4GH {
		return Step{}, false
	}
	return reject(f, "not a Crypt4GH file"), true
}

// rejectNameCollision rejects every file that shares its name with another
// in the batch, because the inbox is flat and one would overwrite the other.
func rejectNameCollision(x uploadIndex, f LocalFile) (Step, bool) {
	others := otherPaths(x.local[f.Name], f)
	if len(others) == 0 {
		return Step{}, false
	}
	return reject(f, fmt.Sprintf("same name as %s, and the inbox is flat", strings.Join(others, ", "))), true
}

func skipInInbox(x uploadIndex, f LocalFile) (Step, bool) {
	if x.opts.Force || !x.inbox[f.Name] {
		return Step{}, false
	}
	return Step{File: f, Action: Skip, Reason: InInbox}, true
}

func resumeInterrupted(x uploadIndex, f LocalFile) (Step, bool) {
	if x.opts.NoResume {
		return Step{}, false
	}
	switch found := x.resumables[f.Name]; len(found) {
	case 0:
		return Step{}, false
	case 1:
		return resumeOne(f, found[0]), true
	default:
		return reject(f, fmt.Sprintf("several interrupted uploads have this name (%s); remove them with lega resumables rm", resumableIDs(found))), true
	}
}

// resumeOne refuses a resumable that holds more bytes than the local file,
// since it cannot belong to this file.
func resumeOne(f LocalFile, r Resumable) Step {
	if r.NextOffset > f.Size {
		return reject(f, fmt.Sprintf("interrupted upload %s holds %d bytes, more than the local file's %d; remove it with lega resumables rm %s", r.ID, r.NextOffset, f.Size, r.ID))
	}
	return Step{File: f, Action: Resume, From: &r}
}

func reject(f LocalFile, reason string) Step {
	return Step{File: f, Action: Reject, Reason: reason}
}

func groupLocal(files []LocalFile) map[string][]LocalFile {
	m := make(map[string][]LocalFile, len(files))
	for _, f := range files {
		m[f.Name] = append(m[f.Name], f)
	}
	return m
}

func groupResumables(rs []Resumable) map[string][]Resumable {
	m := make(map[string][]Resumable, len(rs))
	for _, r := range rs {
		m[r.Name] = append(m[r.Name], r)
	}
	return m
}

func remoteNames(files []RemoteFile) map[string]bool {
	m := make(map[string]bool, len(files))
	for _, f := range files {
		m[f.Name] = true
	}
	return m
}

// otherPaths returns the paths in group other than f's own. Only one
// occurrence of f is dropped, so a path listed twice still collides.
func otherPaths(group []LocalFile, f LocalFile) []string {
	var others []string
	self := false
	for _, g := range group {
		if g.Path == f.Path && !self {
			self = true
			continue
		}
		others = append(others, g.Path)
	}
	return others
}

func resumableIDs(rs []Resumable) string {
	ids := make([]string, len(rs))
	for i, r := range rs {
		ids[i] = r.ID
	}
	return strings.Join(ids, ", ")
}
