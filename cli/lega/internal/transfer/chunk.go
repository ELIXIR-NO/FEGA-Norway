package transfer

import (
	"context"
	"fmt"
	"math/rand/v2"

	"github.com/ELIXIR-NO/FEGA-Norway/cli/lega/internal/plan"
)

// chunkFate is where a chunk stands after its request failed.
type chunkFate int

const (
	// chunkMerged: the backend has the chunk and only its answer was lost.
	chunkMerged chunkFate = iota
	// chunkRolledBack: the backend dropped the chunk, so it is sent again.
	chunkRolledBack
	// chunkLost: the upload is not where this run left it.
	chunkLost
)

// sendChunk sends one chunk and, when that fails, finds out where the
// upload stands before trying again. A chunk is never resent blindly: TSD
// refuses a chunk number it has already merged.
func (u *fileUpload) sendChunk(ctx context.Context, c plan.Chunk, data []byte) error {
	failures := 0
	for {
		id, err := u.up.PutChunk(ctx, u.local.Name, u.uploadID, c, data)
		if err == nil {
			u.uploadID = id
			return nil
		}
		fate, err := u.recoverChunk(ctx, c, err, &failures)
		if err != nil {
			return err
		}
		if fate == chunkMerged {
			return nil
		}
	}
}

// recoverChunk waits out a retryable failure of chunk c and asks the
// backend what became of it. failures counts every failed request for this
// chunk, the lookups included, against Runner.Retries.
func (u *fileUpload) recoverChunk(ctx context.Context, c plan.Chunk, err error, failures *int) (chunkFate, error) {
	for {
		*failures++
		if !u.runner.mayRetry(ctx, err, *failures) {
			return chunkLost, fmt.Errorf("%s: chunk %d: %w", u.local.Path, c.Num, err)
		}
		if serr := u.runner.sleep(ctx, backoff(*failures, rand.Int64N)); serr != nil {
			return chunkLost, serr
		}
		fate, lookupErr := u.resync(ctx, c)
		if lookupErr != nil {
			err = lookupErr
			continue
		}
		if fate == chunkLost {
			return chunkLost, fmt.Errorf("%s: chunk %d failed (%w) and the upload is no longer where it was; run the same command again to resume it", u.local.Path, c.Num, err)
		}
		return fate, nil
	}
}

// resync asks the backend for the upload after chunk c failed.
func (u *fileUpload) resync(ctx context.Context, c plan.Chunk) (chunkFate, error) {
	if u.uploadID == "" {
		return u.adoptOrphan(ctx, c)
	}
	r, ok, err := u.up.Resumable(ctx, u.local.Name, u.uploadID)
	if err != nil {
		return chunkLost, err
	}
	return fateOf(r, ok, c), nil
}

// fateOf reads a chunk's fate from the upload's last merged chunk.
func fateOf(r plan.Resumable, ok bool, c plan.Chunk) chunkFate {
	switch {
	case !ok:
		return chunkLost
	case r.MaxChunk == c.Num:
		return chunkMerged
	case r.MaxChunk == c.Num-1:
		return chunkRolledBack
	default:
		return chunkLost
	}
}

// adoptOrphan handles a first chunk whose answer, and so the upload ID, was
// lost. An upload with this name holding exactly that chunk is taken over;
// otherwise the chunk is sent again, and whatever orphan that leaves is
// caught by the plan's several-resumables rule on the next run.
func (u *fileUpload) adoptOrphan(ctx context.Context, c plan.Chunk) (chunkFate, error) {
	r, ok, err := u.up.Resumable(ctx, u.local.Name, "")
	if err != nil {
		return chunkLost, err
	}
	if !holdsOnly(r, ok, c) {
		return chunkRolledBack, nil
	}
	u.uploadID = r.ID
	return chunkMerged, nil
}

func holdsOnly(r plan.Resumable, ok bool, c plan.Chunk) bool {
	return ok && r.MaxChunk == c.Num && r.NextOffset == c.Offset+c.Len
}
