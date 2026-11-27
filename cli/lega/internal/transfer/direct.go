package transfer

import (
	"context"
	"fmt"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// TSDClient sends chunks straight to the TSD file API. *tsd.Client
// satisfies it.
type TSDClient interface {
	PutChunk(ctx context.Context, fileName, uploadID string, chunk plan.Chunk, data []byte) (string, error)
	Finish(ctx context.Context, fileName, uploadID string) error
}

// Notifier is the part of the proxy a direct upload still needs: the
// resumables listing, and the notification that publishes a finished file.
// *proxy.Client satisfies it.
type Notifier interface {
	Resumable(ctx context.Context, fileName, uploadID string) (plan.Resumable, bool, error)
	Notify(ctx context.Context, fileName string, size int64, sha256 string) error
}

var _ Uploader = DirectUploader{}

// DirectUploader uploads through TSD and tells the proxy when a file is
// complete. chunk=end goes to TSD too, because the TSD token is bound to the
// client's IP (see FEGA-Norway#776).
type DirectUploader struct {
	TSD   TSDClient
	Proxy Notifier
}

// PutChunk sends one chunk to TSD.
func (d DirectUploader) PutChunk(ctx context.Context, fileName, uploadID string, chunk plan.Chunk, data []byte) (string, error) {
	return d.TSD.PutChunk(ctx, fileName, uploadID, chunk, data)
}

// Resumable looks the upload up through the proxy.
func (d DirectUploader) Resumable(ctx context.Context, fileName, uploadID string) (plan.Resumable, bool, error) {
	return d.Proxy.Resumable(ctx, fileName, uploadID)
}

// Finish asks TSD to assemble the file and then has the proxy publish it.
// A failed notification comes back as *NotNotifiedError, since the file is
// in the inbox by then.
func (d DirectUploader) Finish(ctx context.Context, fileName, uploadID string, size int64, sha256 string) error {
	if err := d.TSD.Finish(ctx, fileName, uploadID); err != nil {
		return err
	}
	if err := d.Proxy.Notify(ctx, fileName, size, sha256); err != nil {
		return &NotNotifiedError{Name: fileName, Err: err}
	}
	return nil
}

// NotNotifiedError reports a file that reached the inbox but was never
// announced to the pipeline, so nothing will ingest it.
type NotNotifiedError struct {
	Name string
	Err  error
}

// Error tells the user how to announce the file after all.
func (e *NotNotifiedError) Error() string {
	return fmt.Sprintf("%s was uploaded, but the pipeline was not notified (%v); --force sends it again", e.Name, e.Err)
}

// Unwrap returns the notification's error.
func (e *NotNotifiedError) Unwrap() error { return e.Err }
