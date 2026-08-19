// Package common holds the small shared helpers: random file creation,
// checksums, random numeric strings, JSON compaction and the fixed inter-stage
// wait.
package common

import (
	"crypto/md5"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"math/big"
	"os"
	"path/filepath"
	"time"
)

// NewUUID returns a random RFC-4122 v4 UUID string, used for the unique test
// filename and the AMQP correlation id. The format is not asserted anywhere, so
// a standard-library generator avoids pulling in a dependency.
func NewUUID() string {
	var b [16]byte
	_, _ = rand.Read(b[:])
	b[6] = (b[6] & 0x0f) | 0x40 // version 4
	b[8] = (b[8] & 0x3f) | 0x80 // variant 10
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

// CreateRandomFile writes a UUID-named .raw file of fileSize random bytes under
// basePath and returns its path.
func CreateRandomFile(basePath string, fileSize int64) (string, error) {
	name := filepath.Join(basePath, NewUUID()+".raw")
	f, err := os.Create(name)
	if err != nil {
		return "", err
	}
	defer f.Close()
	if _, err := io.CopyN(f, rand.Reader, fileSize); err != nil {
		return "", err
	}
	return name, nil
}

// Sha256Hex returns the lowercase hex SHA-256 of the file at path.
func Sha256Hex(path string) (string, error) {
	return digestHex(path, sha256.New())
}

// Md5Hex returns the lowercase hex MD5 of the file at path.
func Md5Hex(path string) (string, error) {
	return digestHex(path, md5.New())
}

func digestHex(path string, h io.Writer) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	hash, ok := h.(interface{ Sum([]byte) []byte })
	if !ok {
		return "", fmt.Errorf("hasher does not implement Sum")
	}
	return hex.EncodeToString(hash.Sum(nil)), nil
}

// Sha256HexBytes returns the lowercase hex SHA-256 of b.
func Sha256HexBytes(b []byte) string {
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:])
}

// Md5HexBytes returns the lowercase hex MD5 of b.
func Md5HexBytes(b []byte) string {
	sum := md5.Sum(b)
	return hex.EncodeToString(sum[:])
}

// RandomDigits returns a string of n random decimal digits.
func RandomDigits(n int) string {
	out := make([]byte, n)
	for i := range out {
		d, _ := rand.Int(rand.Reader, big.NewInt(10))
		out[i] = byte('0' + d.Int64())
	}
	return string(out)
}

// CompactJSON parses then re-serializes JSON into canonical compact form.
func CompactJSON(s string) (string, error) {
	var v any
	if err := json.Unmarshal([]byte(s), &v); err != nil {
		return "", err
	}
	b, err := json.Marshal(v)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

// WaitForProcessing sleeps for the given milliseconds. It is a fixed settle
// delay between pipeline stages, a candidate for replacement by bounded polling.
func WaitForProcessing(milliseconds int) {
	time.Sleep(time.Duration(milliseconds) * time.Millisecond)
}
