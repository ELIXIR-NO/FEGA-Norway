package proxy

import (
	"context"
	"crypto/md5" // #nosec G501 -- the proxy checks each chunk against TSD's md5, not for security
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// PutChunk sends one chunk of fileName and returns the upload ID the proxy
// assigns. An empty uploadID starts a new upload. The chunk's md5 goes
// along because the proxy compares it with what TSD received and deletes
// the whole upload on a mismatch.
func (c *Client) PutChunk(ctx context.Context, fileName, uploadID string, chunk plan.Chunk, data []byte) (string, error) {
	var answer struct {
		ID string `json:"id"`
	}
	err := c.call(ctx, request{
		method:   http.MethodPatch,
		segments: []string{"stream", fileName},
		query:    chunkQuery(uploadID, chunk.Num, md5Hex(data)),
		body:     data,
		cega:     true,
	}, &answer)
	if err != nil {
		return "", err
	}
	if answer.ID == "" {
		return "", fmt.Errorf("PATCH /stream/%s: chunk %d: the proxy returned no upload ID", fileName, chunk.Num)
	}
	return answer.ID, nil
}

func chunkQuery(uploadID string, num int64, md5sum string) url.Values {
	q := url.Values{"chunk": {strconv.FormatInt(num, 10)}, "md5": {md5sum}}
	if uploadID != "" {
		q.Set("uploadId", uploadID)
	}
	return q
}

func md5Hex(data []byte) string {
	sum := md5.Sum(data) // #nosec G401
	return hex.EncodeToString(sum[:])
}

// Finish asks the proxy to assemble the upload. size and sha256 describe the
// whole file; the proxy passes them on in the message that starts ingestion.
func (c *Client) Finish(ctx context.Context, fileName, uploadID string, size int64, sha256 string) error {
	return c.call(ctx, request{
		method:   http.MethodPatch,
		segments: []string{"stream", fileName},
		query: url.Values{
			"uploadId": {uploadID},
			"chunk":    {"end"},
			"fileSize": {strconv.FormatInt(size, 10)},
			"sha256":   {sha256},
		},
		cega: true,
	}, nil)
}

// Notify tells the proxy that a file sent straight to TSD is complete, so it
// publishes the inbox message a proxy upload publishes on Finish.
func (c *Client) Notify(ctx context.Context, fileName string, size int64, sha256 string) error {
	return c.call(ctx, request{
		method:   http.MethodPost,
		segments: []string{"stream", fileName, "notify"},
		query: url.Values{
			"fileSize": {strconv.FormatInt(size, 10)},
			"sha256":   {sha256},
		},
		cega: true,
	}, nil)
}

// Download opens fileName in the outbox and returns its body and the length
// the proxy declared, which is -1 when it streams without one. The caller
// closes the body. No CEGA credentials go along: Data Out needs only the
// ELIXIR token.
func (c *Client) Download(ctx context.Context, fileName string) (io.ReadCloser, int64, error) {
	resp, err := c.do(ctx, request{
		method:   http.MethodGet,
		segments: []string{"stream", fileName},
	})
	if err != nil {
		return nil, 0, err
	}
	return resp.Body, resp.ContentLength, nil
}
