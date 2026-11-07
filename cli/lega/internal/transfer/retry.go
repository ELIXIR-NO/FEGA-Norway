package transfer

import (
	"context"
	"errors"
	"io/fs"
	"math/rand/v2"
	"net/http"
	"time"
)

const (
	firstBackoff = time.Second
	maxBackoff   = 30 * time.Second
)

// retryableStatus holds the answers that mean "try again later". A 500 is
// not one: the proxy answers a chunk checksum mismatch with a 500 after
// deleting the whole upload, so a resend cannot succeed.
var retryableStatus = map[int]bool{
	http.StatusTooManyRequests:    true,
	http.StatusBadGateway:         true,
	http.StatusServiceUnavailable: true,
	http.StatusGatewayTimeout:     true,
}

// Retry runs call, and runs it again up to r.Retries times while it fails
// with a retryable error, backing off in between. It suits idempotent
// calls such as listings, never a chunk.
func (r Runner) Retry(ctx context.Context, call func(context.Context) error) error {
	for failures := 1; ; failures++ {
		err := call(ctx)
		if err == nil || !r.mayRetry(ctx, err, failures) {
			return err
		}
		if err := r.sleep(ctx, backoff(failures, rand.Int64N)); err != nil {
			return err
		}
	}
}

// mayRetry reports whether a call that has failed failures times, the last
// time with err, gets another try.
func (r Runner) mayRetry(ctx context.Context, err error, failures int) bool {
	return failures <= r.Retries && ctx.Err() == nil && retryable(err)
}

// retryable is true for an overloaded or unreachable backend: a 429, 502,
// 503 or 504, or an error with no HTTP status at all, which is a transport
// failure, a stall or an answer cut short. A cancelled context never is,
// and neither is a local file error, which a second try cannot fix.
func retryable(err error) bool {
	var pathErr *fs.PathError
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) || errors.As(err, &pathErr) {
		return false
	}
	if status, ok := httpStatus(err); ok {
		return retryableStatus[status]
	}
	return true
}

// statusError is an answer from a backend with an HTTP status;
// *proxy.APIError and *tsd.APIError are both one.
type statusError interface {
	error
	HTTPStatus() int
}

func httpStatus(err error) (int, bool) {
	var se statusError
	if errors.As(err, &se) {
		return se.HTTPStatus(), true
	}
	return 0, false
}

// backoff is the wait before the given retry: exponential from one second,
// capped at 30, with the upper half drawn at random so that clients that
// failed together do not come back together. random returns a value in
// [0, n).
func backoff(failures int, random func(n int64) int64) time.Duration {
	d := maxBackoff
	if failures < 6 {
		d = min(firstBackoff<<(failures-1), maxBackoff)
	}
	half := int64(d / 2)
	return time.Duration(half + random(half))
}

func (r Runner) sleep(ctx context.Context, d time.Duration) error {
	if r.Sleep != nil {
		return r.Sleep(ctx, d)
	}
	return sleepContext(ctx, d)
}

func sleepContext(ctx context.Context, d time.Duration) error {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-t.C:
		return nil
	}
}
