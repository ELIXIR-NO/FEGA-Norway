package proxy

import (
	"context"
	"net/http"
	"testing"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

const resumablesBody = `{"statusCode":200,"resumables":[
 {"id":"U-1","fileName":"a.c4gh","group":"p969-member-group","chunkSize":5,"previousOffset":10,"nextOffset":15,"maxChunk":3,"md5Sum":"abc"},
 {"id":"u-2","fileName":"b.c4gh","nextOffset":5,"maxChunk":1},
 {"id":"u-3","fileName":"b.c4gh","nextOffset":5,"maxChunk":1}]}`

func TestListResumables(t *testing.T) {
	c, log := fakeProxy(t, answer(http.StatusOK, resumablesBody))
	got, err := c.ListResumables(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	want := plan.Resumable{ID: "U-1", Name: "a.c4gh", PreviousOffset: 10, NextOffset: 15, MaxChunk: 3, LastChunkMD5: "abc"}
	if len(got) != 3 || got[0] != want {
		t.Errorf("got %+v, want first %+v", got, want)
	}
	req := log.all()[0]
	if req.method != http.MethodGet || req.path != "/resumables" {
		t.Errorf("sent %s %s", req.method, req.path)
	}
	if _, _, ok := basicAuth(req); !ok {
		t.Error("no CEGA credentials")
	}
}

func TestResumable(t *testing.T) {
	tests := []struct {
		name, fileName, uploadID string
		wantID                   string
		wantOK                   bool
	}{
		{name: "by ID ignoring case", fileName: "a.c4gh", uploadID: "u-1", wantID: "U-1", wantOK: true},
		{name: "unknown ID", fileName: "a.c4gh", uploadID: "u-9"},
		{name: "by name, one match", fileName: "a.c4gh", wantID: "U-1", wantOK: true},
		{name: "by name, several matches", fileName: "b.c4gh"},
		{name: "by name, none", fileName: "c.c4gh"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c, _ := fakeProxy(t, answer(http.StatusOK, resumablesBody))
			got, ok, err := c.Resumable(context.Background(), tt.fileName, tt.uploadID)
			if err != nil {
				t.Fatal(err)
			}
			if ok != tt.wantOK || got.ID != tt.wantID {
				t.Errorf("Resumable = %q, %v; want %q, %v", got.ID, ok, tt.wantID, tt.wantOK)
			}
		})
	}
}

func TestDeleteResumable(t *testing.T) {
	c, log := fakeProxy(t, answer(http.StatusOK, `{}`))
	if err := c.DeleteResumable(context.Background(), "u-1"); err != nil {
		t.Fatal(err)
	}
	req := log.all()[0]
	if req.method != http.MethodDelete || req.path != "/resumables" || req.query.Get("uploadId") != "u-1" {
		t.Errorf("sent %s %s?%s", req.method, req.path, req.rawQuery)
	}
}
