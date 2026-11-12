package proxy

import (
	"context"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// pagedListing answers each wire page with sizes[page] files, and an empty
// list past the end.
func pagedListing(sizes ...int) func(http.ResponseWriter, *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		page, _ := strconv.Atoi(r.URL.Query().Get("page"))
		n := 0
		if page < len(sizes) {
			n = sizes[page]
		}
		entries := make([]string, n)
		for i := range entries {
			entries[i] = fmt.Sprintf(`{"fileName":"p%d-%d.c4gh","size":%d,"modifiedDate":"2026-10-28 14:02:11"}`, page, i, i)
		}
		fmt.Fprintf(w, `{"files":[%s],"page":"%d"}`, strings.Join(entries, ","), page)
	}
}

func TestListFilesPaging(t *testing.T) {
	tests := []struct {
		name      string
		list      func(*Client) ([]plan.RemoteFile, error)
		sizes     []int
		wantPages []string
		wantPer   string
		wantFiles int
		wantErr   bool
	}{
		{
			name:      "first page is wire page 0",
			list:      func(c *Client) ([]plan.RemoteFile, error) { return c.ListFiles(context.Background(), true, 1, 100) },
			sizes:     []int{3},
			wantPages: []string{"0"}, wantPer: "100", wantFiles: 3,
		},
		{
			name:      "third page is wire page 2",
			list:      func(c *Client) ([]plan.RemoteFile, error) { return c.ListFiles(context.Background(), true, 3, 10) },
			sizes:     []int{10, 10, 4},
			wantPages: []string{"2"}, wantPer: "10", wantFiles: 4,
		},
		{
			name:    "page 0 is refused before any request",
			list:    func(c *Client) ([]plan.RemoteFile, error) { return c.ListFiles(context.Background(), true, 0, 10) },
			wantErr: true,
		},
		{
			name:      "all pages stop on a short page",
			list:      func(c *Client) ([]plan.RemoteFile, error) { return c.listAll(context.Background(), true, 2) },
			sizes:     []int{2, 2, 1},
			wantPages: []string{"0", "1", "2"}, wantPer: "2", wantFiles: 5,
		},
		{
			name:      "all pages stop on an empty page",
			list:      func(c *Client) ([]plan.RemoteFile, error) { return c.listAll(context.Background(), false, 2) },
			sizes:     []int{2, 2},
			wantPages: []string{"0", "1", "2"}, wantPer: "2", wantFiles: 4,
		},
		{
			name:      "all files use the TSD page cap",
			list:      func(c *Client) ([]plan.RemoteFile, error) { return c.ListAllFiles(context.Background(), true) },
			sizes:     []int{1},
			wantPages: []string{"0"}, wantPer: "50000", wantFiles: 1,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c, log := fakeProxy(t, pagedListing(tt.sizes...))
			files, err := tt.list(c)
			if (err != nil) != tt.wantErr {
				t.Fatalf("err = %v, wantErr %v", err, tt.wantErr)
			}
			if len(files) != tt.wantFiles {
				t.Errorf("got %d files, want %d", len(files), tt.wantFiles)
			}
			reqs := log.all()
			if len(reqs) != len(tt.wantPages) {
				t.Fatalf("sent %d requests, want %d", len(reqs), len(tt.wantPages))
			}
			for i, req := range reqs {
				if got := req.query.Get("page"); got != tt.wantPages[i] {
					t.Errorf("request %d: page = %s, want %s", i, got, tt.wantPages[i])
				}
				if got := req.query.Get("per_page"); got != tt.wantPer {
					t.Errorf("request %d: per_page = %s, want %s", i, got, tt.wantPer)
				}
			}
		})
	}
}

func TestDecodeListing(t *testing.T) {
	tests := []struct {
		name, body string
		want       int
		wantErr    bool
	}{
		{name: "wrapped", body: `{"files":[{"fileName":"a.c4gh","size":1}],"page":"0"}`, want: 1},
		{name: "bare array", body: `[{"fileName":"a.c4gh"},{"fileName":"b.c4gh"}]`, want: 2},
		{name: "bare empty array", body: `[]`},
		{name: "null files", body: `{"files":null}`},
		{name: "not JSON", body: `<html>`, wantErr: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := decodeListing([]byte(tt.body))
			if (err != nil) != tt.wantErr {
				t.Fatalf("err = %v, wantErr %v", err, tt.wantErr)
			}
			if len(got) != tt.want {
				t.Errorf("got %d entries, want %d", len(got), tt.want)
			}
		})
	}
}

func TestParseModified(t *testing.T) {
	tests := []struct {
		name, in string
		want     time.Time
	}{
		{name: "tsd-file-api isoformat", in: "2026-10-28T14:02:11.123456", want: time.Date(2026, 10, 28, 14, 2, 11, 123456000, time.Local)},
		{name: "isoformat without fraction", in: "2026-10-28T14:02:11", want: time.Date(2026, 10, 28, 14, 2, 11, 0, time.Local)},
		{name: "tsd-api-mock", in: "2026-10-28 14:02:11", want: time.Date(2026, 10, 28, 14, 2, 11, 0, time.Local)},
		{name: "RFC 3339", in: "2026-10-28T14:02:11Z", want: time.Date(2026, 10, 28, 14, 2, 11, 0, time.UTC)},
		{name: "unreadable", in: "yesterday"},
		{name: "empty", in: ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := parseModified(tt.in); !got.Equal(tt.want) {
				t.Errorf("parseModified(%q) = %v, want %v", tt.in, got, tt.want)
			}
		})
	}
}

func TestDeleteFile(t *testing.T) {
	c, log := fakeProxy(t, answer(http.StatusOK, `{}`))
	if err := c.DeleteFile(context.Background(), "a+b.c4gh"); err != nil {
		t.Fatal(err)
	}
	req := log.all()[0]
	if req.method != http.MethodDelete || req.path != "/files" || req.query.Get("fileName") != "a+b.c4gh" {
		t.Errorf("sent %s %s?%s", req.method, req.path, req.rawQuery)
	}
	if req.header.Get("Authorization") == "" {
		t.Error("delete carries no CEGA credentials")
	}
}
