// Package c4gh wraps github.com/neicnordic/crypt4gh for the operations the suite
// needs: keypair generation, reading an archive public key, encrypting the test
// file, writing a public key in Crypt4GH armored form (for the DOA Public-Key
// header) and decrypting a downloaded file.
package c4gh

import (
	"bytes"
	"io"
	"os"

	"github.com/neicnordic/crypt4gh/keys"
	"github.com/neicnordic/crypt4gh/streaming"
)

// KeyPair is an X25519 Crypt4GH keypair.
type KeyPair struct {
	Public  [32]byte
	Private [32]byte
}

// GenerateKeyPair generates a fresh X25519 Crypt4GH keypair.
func GenerateKeyPair() (KeyPair, error) {
	pub, private, err := keys.GenerateKeyPair()
	if err != nil {
		return KeyPair{}, err
	}
	return KeyPair{Public: pub, Private: private}, nil
}

// ReadPublicKeyFile reads a Crypt4GH-format public key from path.
func ReadPublicKeyFile(path string) ([32]byte, error) {
	f, err := os.Open(path)
	if err != nil {
		return [32]byte{}, err
	}
	defer f.Close()
	return keys.ReadPublicKey(f)
}

// Encrypt writes a Crypt4GH-encrypted copy of srcPath to dstPath, sealed from
// senderPriv to recipientPub.
func Encrypt(dstPath, srcPath string, senderPriv [32]byte, recipientPub [32]byte) error {
	src, err := os.Open(srcPath)
	if err != nil {
		return err
	}
	defer src.Close()

	dst, err := os.Create(dstPath)
	if err != nil {
		return err
	}
	defer dst.Close()

	w, err := streaming.NewCrypt4GHWriter(dst, senderPriv, [][32]byte{recipientPub}, nil)
	if err != nil {
		return err
	}
	if _, err := io.Copy(w, src); err != nil {
		return err
	}
	return w.Close()
}

// PublicKeyArmored renders pub in Crypt4GH armored PEM form (used for the DOA
// "Public-Key" header).
func PublicKeyArmored(pub [32]byte) (string, error) {
	var buf bytes.Buffer
	if err := keys.WriteCrypt4GHX25519PublicKey(&buf, pub); err != nil {
		return "", err
	}
	return buf.String(), nil
}

// Decrypt reads a Crypt4GH stream from r and returns the plaintext, decrypting
// with recipientPriv.
func Decrypt(r io.Reader, recipientPriv [32]byte) ([]byte, error) {
	cr, err := streaming.NewCrypt4GHReader(r, recipientPriv, nil)
	if err != nil {
		return nil, err
	}
	return io.ReadAll(cr)
}
