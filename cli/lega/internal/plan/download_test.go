package plan

import (
	"reflect"
	"strings"
	"testing"
	"time"
)

func TestPlanDownload(t *testing.T) {
	exported := time.Date(2026, 10, 28, 14, 2, 0, 0, time.UTC)
	later := exported.Add(time.Hour)
	outbox := []RemoteFile{{Name: "a.c4gh", Size: 10, Modified: exported}, {Name: "b.c4gh", Size: 20, Modified: exported}}
	tests := []struct {
		name       string
		names      []string
		outbox     []RemoteFile
		local      map[string]LocalCopy
		want       []DownloadStep
		wantReason []string // substring per step, "" to skip the check
	}{
		{
			name:   "existing_same_size_skips",
			names:  []string{"a.c4gh"},
			outbox: outbox,
			local:  map[string]LocalCopy{"a.c4gh": {Size: 10, Modified: later}},
			want:   []DownloadStep{{Name: "a.c4gh", Dir: "out", Action: Skip, Size: 10, Modified: exported}},
		},
		{
			name:   "existing_same_time_as_export_skips",
			names:  []string{"a.c4gh"},
			outbox: outbox,
			local:  map[string]LocalCopy{"a.c4gh": {Size: 10, Modified: exported}},
			want:   []DownloadStep{{Name: "a.c4gh", Dir: "out", Action: Skip, Size: 10, Modified: exported}},
		},
		{
			name:       "existing_older_than_export_rejects",
			names:      []string{"a.c4gh"},
			outbox:     outbox,
			local:      map[string]LocalCopy{"a.c4gh": {Size: 10, Modified: exported.Add(-time.Hour)}},
			want:       []DownloadStep{{Name: "a.c4gh", Dir: "out", Action: Reject, Size: 10, Modified: exported}},
			wantReason: []string{"exported 2026-10-28 14:02"},
		},
		{
			name:       "existing_other_size_rejects",
			names:      []string{"a.c4gh"},
			outbox:     outbox,
			local:      map[string]LocalCopy{"a.c4gh": {Size: 4, Modified: later}},
			want:       []DownloadStep{{Name: "a.c4gh", Dir: "out", Action: Reject, Size: 10, Modified: exported}},
			wantReason: []string{"4 bytes"},
		},
		{
			name:       "not_in_outbox_rejects",
			names:      []string{"missing.c4gh", "b.c4gh"},
			outbox:     outbox,
			want:       []DownloadStep{{Name: "missing.c4gh", Dir: "out", Action: Reject}, {Name: "b.c4gh", Dir: "out", Action: Fetch, Size: 20, Modified: exported}},
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
			local:  map[string]LocalCopy{"b.c4gh": {Size: 20, Modified: later}},
			want: []DownloadStep{
				{Name: "a.c4gh", Dir: "out", Action: Fetch, Size: 10, Modified: exported},
				{Name: "b.c4gh", Dir: "out", Action: Skip, Size: 20, Modified: exported},
			},
		},
		{
			name:   "name_given_twice_planned_once",
			names:  []string{"a.c4gh", "b.c4gh", "a.c4gh"},
			outbox: outbox,
			want: []DownloadStep{
				{Name: "a.c4gh", Dir: "out", Action: Fetch, Size: 10, Modified: exported},
				{Name: "b.c4gh", Dir: "out", Action: Fetch, Size: 20, Modified: exported},
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
