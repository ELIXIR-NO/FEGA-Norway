package transfer

import (
	"io"

	"github.com/neicnordic/crypt4gh/model/headers"
)

// isCrypt4GH reports whether r starts with a well-formed Crypt4GH header.
// It checks structure only, not that the FEGA key is a recipient.
func isCrypt4GH(r io.Reader) bool {
	_, err := headers.ReadHeader(r)
	return err == nil
}
