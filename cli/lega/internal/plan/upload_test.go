package plan

import (
	"reflect"
	"strings"
	"testing"
)

func c4gh(path string, size int64) LocalFile {
	name := path[strings.LastIndex(path, "/")+1:]
	return LocalFile{Path: path, Name: name, Size: size, Crypt4GH: true}
}

func TestPlanUpload(t *testing.T) {
	a := c4gh("in/a.c4gh", 100)
	b := c4gh("in/b.c4gh", 100)
	notes := LocalFile{Path: "in/notes.txt", Name: "notes.txt", Size: 10}
	xa := c4gh("in/a/x.c4gh", 100)
	xb := c4gh("in/b/x.c4gh", 100)
	resA := Resumable{ID: "r1", Name: "a.c4gh", PreviousOffset: 40, NextOffset: 60, MaxChunk: 3}
	resA2 := Resumable{ID: "r2", Name: "a.c4gh", NextOffset: 20, MaxChunk: 1}
	big := Resumable{ID: "r9", Name: "a.c4gh", NextOffset: 101, MaxChunk: 9}

	tests := []struct {
		name       string
		local      []LocalFile
		inbox      []RemoteFile
		resumables []Resumable
		opts       UploadOptions
		want       []Action
		wantFrom   []string // resumable ID per step, "" for none
		wantReason []string // substring per step, "" to skip the check
	}{
		{
			name:       "not_crypt4gh_rejects",
			local:      []LocalFile{notes, a},
			want:       []Action{Reject, Upload},
			wantReason: []string{"not a Crypt4GH file", ""},
		},
		{
			name:       "name_collision_rejects_both",
			local:      []LocalFile{xa, xb, a},
			want:       []Action{Reject, Reject, Upload},
			wantReason: []string{"same name as in/b/x.c4gh", "same name as in/a/x.c4gh", ""},
		},
		{
			name:       "not_crypt4gh_wins_over_collision",
			local:      []LocalFile{{Path: "in/c/x.c4gh", Name: "x.c4gh", Size: 1}, xa},
			want:       []Action{Reject, Reject},
			wantReason: []string{"not a Crypt4GH file", "same name as in/c/x.c4gh"},
		},
		{
			name:       "same_path_twice_collides",
			local:      []LocalFile{a, a},
			want:       []Action{Reject, Reject},
			wantReason: []string{"same name as in/a.c4gh", "same name as in/a.c4gh"},
		},
		{
			name:       "already_in_inbox_skips",
			local:      []LocalFile{a, b},
			inbox:      []RemoteFile{{Name: "a.c4gh", Size: 100}},
			resumables: []Resumable{resA},
			want:       []Action{Skip, Upload},
			wantReason: []string{"already in the inbox", ""},
		},
		{
			name:  "force_uploads_over_inbox",
			local: []LocalFile{a},
			inbox: []RemoteFile{{Name: "a.c4gh", Size: 100}},
			opts:  UploadOptions{Force: true},
			want:  []Action{Upload},
		},
		{
			name:       "force_resumes_over_inbox",
			local:      []LocalFile{a},
			inbox:      []RemoteFile{{Name: "a.c4gh", Size: 100}},
			resumables: []Resumable{resA},
			opts:       UploadOptions{Force: true},
			want:       []Action{Resume},
			wantFrom:   []string{"r1"},
		},
		{
			name:       "no_resumable_uploads_fresh",
			local:      []LocalFile{a},
			resumables: []Resumable{{ID: "r5", Name: "other.c4gh", NextOffset: 10}},
			want:       []Action{Upload},
		},
		{
			name:       "folder_resumes_one_uploads_rest",
			local:      []LocalFile{a, b, c4gh("in/c.c4gh", 5)},
			resumables: []Resumable{resA},
			want:       []Action{Resume, Upload, Upload},
			wantFrom:   []string{"r1", "", ""},
		},
		{
			name:       "resumable_equal_to_local_file_resumes",
			local:      []LocalFile{a},
			resumables: []Resumable{{ID: "r3", Name: "a.c4gh", NextOffset: 100}},
			want:       []Action{Resume},
			wantFrom:   []string{"r3"},
		},
		{
			name:       "resumable_larger_than_local_file",
			local:      []LocalFile{a},
			resumables: []Resumable{big},
			want:       []Action{Reject},
			wantReason: []string{"lega resumables rm r9"},
		},
		{
			name:       "several_resumables_reject",
			local:      []LocalFile{a},
			resumables: []Resumable{resA, resA2},
			want:       []Action{Reject},
			wantReason: []string{"r1, r2"},
		},
		{
			name:       "no_resume_flag_uploads_fresh",
			local:      []LocalFile{a, b},
			resumables: []Resumable{resA, resA2, {ID: "r4", Name: "b.c4gh", NextOffset: 1}},
			opts:       UploadOptions{NoResume: true},
			want:       []Action{Upload, Upload},
		},
		{
			name: "empty_batch",
			want: []Action{},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			steps := PlanUpload(tt.local, tt.inbox, tt.resumables, tt.opts)
			if got := actions(steps); !reflect.DeepEqual(got, tt.want) {
				t.Fatalf("actions = %v, want %v", got, tt.want)
			}
			for i, s := range steps {
				if s.File != tt.local[i] {
					t.Errorf("step %d file = %+v, want %+v", i, s.File, tt.local[i])
				}
				checkFrom(t, i, s, tt.wantFrom)
				checkReason(t, i, s.Reason, tt.wantReason)
			}
		})
	}
}

func actions(steps []Step) []Action {
	got := make([]Action, len(steps))
	for i, s := range steps {
		got[i] = s.Action
	}
	return got
}

func checkFrom(t *testing.T, i int, s Step, want []string) {
	t.Helper()
	id := ""
	if s.From != nil {
		id = s.From.ID
	}
	if want == nil {
		if id != "" {
			t.Errorf("step %d From = %q, want none", i, id)
		}
		return
	}
	if id != want[i] {
		t.Errorf("step %d From = %q, want %q", i, id, want[i])
	}
}

func checkReason(t *testing.T, i int, got string, want []string) {
	t.Helper()
	if want == nil || want[i] == "" {
		return
	}
	if !strings.Contains(got, want[i]) {
		t.Errorf("step %d reason = %q, want it to contain %q", i, got, want[i])
	}
}

func TestPlanUploadFromIsACopy(t *testing.T) {
	resumables := []Resumable{{ID: "r1", Name: "a.c4gh", NextOffset: 10}}
	steps := PlanUpload([]LocalFile{c4gh("a.c4gh", 20)}, nil, resumables, UploadOptions{})
	resumables[0].ID = "changed"
	if steps[0].From.ID != "r1" {
		t.Errorf("From aliases the caller's slice: ID = %q", steps[0].From.ID)
	}
}
