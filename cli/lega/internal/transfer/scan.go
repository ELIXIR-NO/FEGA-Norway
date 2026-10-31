package transfer

import (
	"io/fs"
	"os"
	"path/filepath"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// Scan finds every regular file under paths, walking folders recursively,
// and reads each file's header to tell whether it is Crypt4GH. A path named
// twice, directly or through a folder, is returned once, since the plan
// would otherwise reject it as colliding with itself. Name is the base
// name, because the inbox is flat.
func Scan(paths []string) ([]plan.LocalFile, error) {
	var files []plan.LocalFile
	seen := make(map[string]bool)
	for _, root := range paths {
		found, err := scanPath(root)
		if err != nil {
			return nil, err
		}
		for _, f := range found {
			if key := pathKey(f.Path); !seen[key] {
				seen[key] = true
				files = append(files, f)
			}
		}
	}
	return files, nil
}

// scanPath walks root. A root that is a symlink to a folder is walked
// through the link, since the user named it; symlinked folders found inside
// a walk are not, which keeps a link loop from walking forever.
func scanPath(root string) ([]plan.LocalFile, error) {
	var files []plan.LocalFile
	err := filepath.WalkDir(followLinkedFolder(root), func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		f, ok, err := scanFile(path)
		if ok {
			files = append(files, f)
		}
		return err
	})
	return files, err
}

// followLinkedFolder adds a trailing separator to a symlink that points to a
// folder, which makes WalkDir resolve the link instead of reporting it as a
// file.
func followLinkedFolder(root string) string {
	link, err := os.Lstat(root)
	if err != nil || link.Mode()&fs.ModeSymlink == 0 {
		return root
	}
	if target, err := os.Stat(root); err == nil && target.IsDir() {
		return root + string(filepath.Separator)
	}
	return root
}

// scanFile reports ok false for anything that is not a regular file once
// symlinks are followed, such as a socket or a link to a folder met inside
// a walk.
func scanFile(path string) (plan.LocalFile, bool, error) {
	f, err := os.Open(path)
	if err != nil {
		return plan.LocalFile{}, false, err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil || !info.Mode().IsRegular() {
		return plan.LocalFile{}, false, err
	}
	return plan.LocalFile{
		Path:     path,
		Name:     filepath.Base(path),
		Size:     info.Size(),
		Crypt4GH: isCrypt4GH(f),
	}, true, nil
}

// pathKey makes ./a/x and a/x the same file.
func pathKey(path string) string {
	if abs, err := filepath.Abs(path); err == nil {
		return abs
	}
	return filepath.Clean(path)
}
