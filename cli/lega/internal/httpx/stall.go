package httpx

import (
	"bytes"
	"context"
	"errors"
	"io"
	"sync"
	"time"
)

// StallTimeout is how long a request or response body may go without moving
// a byte before its request is cancelled.
const StallTimeout = 2 * time.Minute

// ErrStalled reports a body that stopped moving. It carries no HTTP status,
// so a caller may retry it like any other transport failure.
var ErrStalled = errors.New("the connection stalled: no data moved for too long")

// StallGuard cancels a request when one of its bodies makes no progress for
// a while. It watches only while a body is moving: the wait for the answer's
// headers belongs to the transport's own timeout, since the proxy takes
// minutes to answer chunk=end on a large file.
type StallGuard struct {
	ctx    context.Context
	cancel context.CancelCauseFunc
	idle   time.Duration

	mu    sync.Mutex
	timer *time.Timer
}

// NewStallGuard returns a guard whose Context the request must use. idle is
// how long a body may stand still.
func NewStallGuard(ctx context.Context, idle time.Duration) *StallGuard {
	ctx, cancel := context.WithCancelCause(ctx)
	return &StallGuard{ctx: ctx, cancel: cancel, idle: idle}
}

// Context is the context the guarded request runs under.
func (g *StallGuard) Context() context.Context { return g.ctx }

// Release stops the guard and cancels its context; call it once the
// request and its answer are done with.
func (g *StallGuard) Release() {
	g.pause()
	g.cancel(nil)
}

// Err returns ErrStalled when the guard cancelled the request, and err as
// it is otherwise, so a stall is not mistaken for the user's Ctrl-C.
func (g *StallGuard) Err(err error) error {
	if err != nil && errors.Is(context.Cause(g.ctx), ErrStalled) {
		return ErrStalled
	}
	return err
}

// RequestBody returns a reader over data that keeps the guard awake while
// the transport reads it and puts it to sleep once all of data is read.
func (g *StallGuard) RequestBody(data []byte) io.Reader {
	return &sendBody{r: bytes.NewReader(data), g: g}
}

// ResponseBody wraps an answer's body so the guard watches it until it is
// closed. Closing it releases the guard.
func (g *StallGuard) ResponseBody(body io.ReadCloser) io.ReadCloser {
	g.touch()
	return &receiveBody{rc: body, g: g}
}

func (g *StallGuard) touch() {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.timer == nil {
		g.timer = time.AfterFunc(g.idle, func() { g.cancel(ErrStalled) })
		return
	}
	g.timer.Reset(g.idle)
}

func (g *StallGuard) pause() {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.timer != nil {
		g.timer.Stop()
	}
}

type sendBody struct {
	r *bytes.Reader
	g *StallGuard
}

func (b *sendBody) Read(p []byte) (int, error) {
	b.g.touch()
	n, err := b.r.Read(p)
	if err == io.EOF {
		b.g.pause()
	}
	return n, err
}

type receiveBody struct {
	rc io.ReadCloser
	g  *StallGuard
}

func (b *receiveBody) Read(p []byte) (int, error) {
	n, err := b.rc.Read(p)
	if n > 0 {
		b.g.touch()
	}
	if err != nil && err != io.EOF {
		err = b.g.Err(err)
	}
	return n, err
}

func (b *receiveBody) Close() error {
	err := b.rc.Close()
	b.g.Release()
	return err
}
