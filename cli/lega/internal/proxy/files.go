package proxy

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"time"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// MaxPerPage is tsd-file-api's own per_page cap. A full listing asks for
// pages this size, so it takes as few requests as TSD allows.
const MaxPerPage = 50000

// ListFiles returns one page of the inbox, or of the outbox when inbox is
// false. page is 1-based, as the user gives it; the proxy counts from 0.
func (c *Client) ListFiles(ctx context.Context, inbox bool, page, perPage int) ([]plan.RemoteFile, error) {
	if page < 1 {
		return nil, fmt.Errorf("page %d: pages start at 1", page)
	}
	return c.listPage(ctx, inbox, page-1, perPage)
}

// ListAllFiles returns every file in the inbox, or in the outbox when inbox
// is false, paging until the proxy returns a short page.
func (c *Client) ListAllFiles(ctx context.Context, inbox bool) ([]plan.RemoteFile, error) {
	return c.listAll(ctx, inbox, MaxPerPage)
}

func (c *Client) listAll(ctx context.Context, inbox bool, perPage int) ([]plan.RemoteFile, error) {
	var all []plan.RemoteFile
	for wirePage := 0; ; wirePage++ {
		files, err := c.listPage(ctx, inbox, wirePage, perPage)
		if err != nil {
			return nil, err
		}
		all = append(all, files...)
		if len(files) < perPage {
			return all, nil
		}
	}
}

// listPage fetches one 0-based page. The outbox listing carries no CEGA
// credentials: Data Out needs only the ELIXIR token.
func (c *Client) listPage(ctx context.Context, inbox bool, wirePage, perPage int) ([]plan.RemoteFile, error) {
	var raw json.RawMessage
	err := c.call(ctx, request{
		method:   http.MethodGet,
		segments: []string{"files"},
		query: url.Values{
			"inbox":    {strconv.FormatBool(inbox)},
			"page":     {strconv.Itoa(wirePage)},
			"per_page": {strconv.Itoa(perPage)},
		},
		cega:    inbox,
		bounded: true,
	}, &raw)
	if err != nil {
		return nil, err
	}
	entries, err := decodeListing(raw)
	if err != nil {
		return nil, fmt.Errorf("GET /files: read answer: %w", err)
	}
	return remoteFiles(entries), nil
}

// DeleteFile removes name from the inbox.
func (c *Client) DeleteFile(ctx context.Context, fileName string) error {
	return c.call(ctx, request{
		method:   http.MethodDelete,
		segments: []string{"files"},
		query:    url.Values{"fileName": {fileName}},
		cega:     true,
		bounded:  true,
	}, nil)
}

// fileEntry is one file as the proxy lists it.
type fileEntry struct {
	FileName     string `json:"fileName"`
	Size         int64  `json:"size"`
	ModifiedDate string `json:"modifiedDate"`
}

// decodeListing accepts both a wrapped {"files": [...]} and a bare array,
// which is how an empty listing comes back once the proxy has error codes.
// A null files member is an empty listing.
func decodeListing(raw []byte) ([]fileEntry, error) {
	var entries []fileEntry
	if bytes.HasPrefix(bytes.TrimSpace(raw), []byte("[")) {
		err := json.Unmarshal(raw, &entries)
		return entries, err
	}
	var wrapped struct {
		Files []fileEntry `json:"files"`
	}
	err := json.Unmarshal(raw, &wrapped)
	return wrapped.Files, err
}

func isEmptyListing(body string) bool {
	entries, err := decodeListing([]byte(body))
	return err == nil && len(entries) == 0
}

func remoteFiles(entries []fileEntry) []plan.RemoteFile {
	files := make([]plan.RemoteFile, 0, len(entries))
	for _, e := range entries {
		files = append(files, plan.RemoteFile{Name: e.FileName, Size: e.Size, Modified: parseModified(e.ModifiedDate)})
	}
	return files
}

// modifiedLayouts are the shapes modifiedDate arrives in: tsd-file-api sends
// Python's isoformat() of a local time, tsd-api-mock a plain date and time.
var modifiedLayouts = []string{
	time.RFC3339Nano,
	"2006-01-02T15:04:05.999999999",
	"2006-01-02 15:04:05",
}

// parseModified reads a listing's modifiedDate. TSD sends its own local time
// with no zone, so a zoneless time is read in the local zone. An unreadable
// date is the zero time rather than a failed listing, since the date is
// only displayed.
func parseModified(s string) time.Time {
	for _, layout := range modifiedLayouts {
		if t, err := time.ParseInLocation(layout, s, time.Local); err == nil {
			return t
		}
	}
	return time.Time{}
}
