package proxy

import (
	"context"
	"net/http"
	"net/url"
	"strings"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// resumableEntry is one interrupted upload as the proxy lists it.
type resumableEntry struct {
	ID             string `json:"id"`
	FileName       string `json:"fileName"`
	PreviousOffset int64  `json:"previousOffset"`
	NextOffset     int64  `json:"nextOffset"`
	MaxChunk       int64  `json:"maxChunk"`
	MD5Sum         string `json:"md5Sum"`
}

// ListResumables returns every interrupted upload the user has.
func (c *Client) ListResumables(ctx context.Context) ([]plan.Resumable, error) {
	var listing struct {
		Resumables []resumableEntry `json:"resumables"`
	}
	err := c.call(ctx, request{
		method:   http.MethodGet,
		segments: []string{"resumables"},
		cega:     true,
		bounded:  true,
	}, &listing)
	if err != nil {
		return nil, err
	}
	return resumables(listing.Resumables), nil
}

// Resumable looks up one interrupted upload. With an uploadID it matches
// the ID, ignoring case as the proxy does; with an empty uploadID it matches
// fileName and reports ok only when exactly one upload has that name, which
// is how a lost answer to the first chunk is recovered. It lists rather than
// asking the proxy for one ID, because the proxy answers that route with a
// Java Optional whose shape for a missing ID is not pinned down.
func (c *Client) Resumable(ctx context.Context, fileName, uploadID string) (plan.Resumable, bool, error) {
	all, err := c.ListResumables(ctx)
	if err != nil {
		return plan.Resumable{}, false, err
	}
	r, ok := findResumable(all, fileName, uploadID)
	return r, ok, nil
}

func findResumable(all []plan.Resumable, fileName, uploadID string) (plan.Resumable, bool) {
	var found []plan.Resumable
	for _, r := range all {
		if matchesResumable(r, fileName, uploadID) {
			found = append(found, r)
		}
	}
	if len(found) != 1 {
		return plan.Resumable{}, false
	}
	return found[0], true
}

func matchesResumable(r plan.Resumable, fileName, uploadID string) bool {
	if uploadID != "" {
		return strings.EqualFold(r.ID, uploadID)
	}
	return r.Name == fileName
}

// DeleteResumable discards the interrupted upload with the given ID.
func (c *Client) DeleteResumable(ctx context.Context, uploadID string) error {
	return c.call(ctx, request{
		method:   http.MethodDelete,
		segments: []string{"resumables"},
		query:    url.Values{"uploadId": {uploadID}},
		cega:     true,
		bounded:  true,
	}, nil)
}

// resumables keeps maxChunk as the proxy reports it, the last chunk merged;
// the plan decides which chunk comes next.
func resumables(entries []resumableEntry) []plan.Resumable {
	out := make([]plan.Resumable, 0, len(entries))
	for _, e := range entries {
		out = append(out, plan.Resumable{
			ID:             e.ID,
			Name:           e.FileName,
			PreviousOffset: e.PreviousOffset,
			NextOffset:     e.NextOffset,
			MaxChunk:       e.MaxChunk,
			LastChunkMD5:   e.MD5Sum,
		})
	}
	return out
}
