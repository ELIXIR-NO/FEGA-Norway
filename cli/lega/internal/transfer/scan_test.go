package transfer

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

func TestScan(t *testing.T) {
	root := t.TempDir()
	c4gh := encrypted(t)
	files := map[string][]byte{
		"a/x.c4gh":    c4gh,
		"a/notes.txt": []byte("plain text"),
		"b/x.c4gh":    c4gh,
		"top.c4gh":    c4gh,
	}
	if err := os.MkdirAll(filepath.Join(root, "empty"), 0o700); err != nil {
		t.Fatal(err)
	}
	for name, data := range files {
		path := filepath.Join(root, name)
		if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(path, data, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	at := func(name string) string { return filepath.Join(root, name) }
	if err := os.Symlink(at("b"), at("linked")); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(at("a"), at("b/loop")); err != nil {
		t.Fatal(err)
	}
	size := int64(len(c4gh))
	tests := []struct {
		name    string
		paths   []string
		want    []plan.LocalFile
		wantErr bool
	}{
		{
			name:  "one file",
			paths: []string{at("top.c4gh")},
			want:  []plan.LocalFile{{Path: at("top.c4gh"), Name: "top.c4gh", Size: size, Crypt4GH: true}},
		},
		{
			name:  "folder walked recursively",
			paths: []string{at("a"), at("b")},
			want: []plan.LocalFile{
				{Path: at("a/notes.txt"), Name: "notes.txt", Size: 10},
				{Path: at("a/x.c4gh"), Name: "x.c4gh", Size: size, Crypt4GH: true},
				{Path: at("b/x.c4gh"), Name: "x.c4gh", Size: size, Crypt4GH: true},
			},
		},
		{
			name:  "same file named twice",
			paths: []string{at("top.c4gh"), root + "/./top.c4gh"},
			want:  []plan.LocalFile{{Path: at("top.c4gh"), Name: "top.c4gh", Size: size, Crypt4GH: true}},
		},
		{
			name:  "file inside a folder named too",
			paths: []string{at("b"), at("b/x.c4gh")},
			want:  []plan.LocalFile{{Path: at("b/x.c4gh"), Name: "x.c4gh", Size: size, Crypt4GH: true}},
		},
		{
			name:  "symlink to a folder named directly is walked",
			paths: []string{at("linked")},
			want:  []plan.LocalFile{{Path: at("linked/x.c4gh"), Name: "x.c4gh", Size: size, Crypt4GH: true}},
		},
		{name: "empty folder", paths: []string{at("empty")}, want: nil},
		{name: "missing path", paths: []string{at("nope")}, wantErr: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := Scan(tt.paths)
			if (err != nil) != tt.wantErr {
				t.Fatalf("err = %v, wantErr %v", err, tt.wantErr)
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Scan =\n%+v\nwant\n%+v", got, tt.want)
			}
		})
	}
}
