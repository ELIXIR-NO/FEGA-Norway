package plan

import (
	"reflect"
	"strings"
	"testing"
)

func TestPlanDownload(t *testing.T) {
	outbox := []RemoteFile{{Name: "a.c4gh", Size: 10}, {Name: "b.c4gh", Size: 20}}
	tests := []struct {
		name       string
		names      []string
		outbox     []RemoteFile
		local      map[string]int64
		want       []DownloadStep
		wantReason []string // substring per step, "" to skip the check
	}{
		{
			name:   "existing_same_size_skips",
			names:  []string{"a.c4gh"},
			outbox: outbox,
			local:  map[string]int64{"a.c4gh": 10},
			want:   []DownloadStep{{Name: "a.c4gh", Dir: "out", Action: Skip, Size: 10}},
		},
		{
			name:       "existing_other_size_rejects",
			names:      []string{"a.c4gh"},
			outbox:     outbox,
			local:      map[string]int64{"a.c4gh": 4},
			want:       []DownloadStep{{Name: "a.c4gh", Dir: "out", Action: Reject, Size: 10}},
			wantReason: []string{"4 bytes"},
		},
		{
			name:       "not_in_outbox_rejects",
			names:      []string{"missing.c4gh", "b.c4gh"},
			outbox:     outbox,
			want:       []DownloadStep{{Name: "missing.c4gh", Dir: "out", Action: Reject}, {Name: "b.c4gh", Dir: "out", Action: Fetch, Size: 20}},
			wantReason: []string{"not in the outbox", ""},
		},
		{
			name: "bad_name_rejects",
			outbox: []RemoteFile{
				{Name: "", Size: 1}, {Name: ".", Size: 1}, {Name: "..", Size: 1},
				{Name: "a/b", Size: 1}, {Name: `a\b`, Size: 1}, {Name: "ok.c4gh", Size: 1},
			},
			want: []DownloadStep{
				{Name: "", Dir: "out", Action: Reject, Size: 1},
				{Name: ".", Dir: "out", Action: Reject, Size: 1},
				{Name: "..", Dir: "out", Action: Reject, Size: 1},
				{Name: "a/b", Dir: "out", Action: Reject, Size: 1},
				{Name: `a\b`, Dir: "out", Action: Reject, Size: 1},
				{Name: "ok.c4gh", Dir: "out", Action: Fetch, Size: 1},
			},
			wantReason: []string{"plain file name", "plain file name", "plain file name", "plain file name", "plain file name", ""},
		},
		{
			name:   "whole_outbox_when_no_names",
			outbox: outbox,
			local:  map[string]int64{"b.c4gh": 20},
			want: []DownloadStep{
				{Name: "a.c4gh", Dir: "out", Action: Fetch, Size: 10},
				{Name: "b.c4gh", Dir: "out", Action: Skip, Size: 20},
			},
		},
		{
			name:   "name_given_twice_planned_once",
			names:  []string{"a.c4gh", "b.c4gh", "a.c4gh"},
			outbox: outbox,
			want: []DownloadStep{
				{Name: "a.c4gh", Dir: "out", Action: Fetch, Size: 10},
				{Name: "b.c4gh", Dir: "out", Action: Fetch, Size: 20},
			},
		},
		{
			name: "empty_outbox_plans_nothing",
			want: []DownloadStep{},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := PlanDownload(tt.names, tt.outbox, tt.local, "out")
			for i := range got {
				if tt.wantReason != nil && !strings.Contains(got[i].Reason, tt.wantReason[i]) {
					t.Errorf("step %d reason = %q, want it to contain %q", i, got[i].Reason, tt.wantReason[i])
				}
				if (got[i].Action == Skip || got[i].Action == Reject) && got[i].Reason == "" {
					t.Errorf("step %d is %v with no reason", i, got[i].Action)
				}
				got[i].Reason = ""
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("steps = %+v, want %+v", got, tt.want)
			}
		})
	}
}
