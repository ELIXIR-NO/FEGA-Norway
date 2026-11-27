package transfer

import (
	"bytes"
	"io"
	"strings"
	"testing"

	"github.com/neicnordic/crypt4gh/keys"
	"github.com/neicnordic/crypt4gh/streaming"
)

func encrypted(t *testing.T) []byte {
	t.Helper()
	public, private, err := keys.GenerateKeyPair()
	if err != nil {
		t.Fatal(err)
	}
	var buf bytes.Buffer
	w, err := streaming.NewCrypt4GHWriter(&buf, private, [][32]byte{public}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := w.Write([]byte("payload")); err != nil {
		t.Fatal(err)
	}
	if err := w.Close(); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func TestIsCrypt4GH(t *testing.T) {
	tests := []struct {
		name string
		r    io.Reader
		want bool
	}{
		{name: "encrypted file", r: bytes.NewReader(encrypted(t)), want: true},
		{name: "plain text", r: strings.NewReader("just some text, not encrypted"), want: false},
		{name: "magic number only", r: strings.NewReader("crypt4gh"), want: false},
		{name: "empty", r: strings.NewReader(""), want: false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := isCrypt4GH(tt.r); got != tt.want {
				t.Errorf("isCrypt4GH = %v, want %v", got, tt.want)
			}
		})
	}
}
