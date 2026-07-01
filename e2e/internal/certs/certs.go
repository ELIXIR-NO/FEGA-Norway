// Package certs locates the TLS/crypto material the suite needs. It is
// container-only: the file-orchestrator stages everything into /storage/certs
// and the runner reads it directly from the mounted volume.
package certs

import (
	"crypto/x509"
	"fmt"
	"os"
	"path/filepath"
)

// CertsDir is where the file-orchestrator stages all generated certs/keys.
const CertsDir = "/storage/certs"

// CertFile returns the path to a file under /storage/certs, erroring if it does
// not exist.
func CertFile(name string) (string, error) {
	return File(filepath.Join(CertsDir, name))
}

// File returns absPath, erroring if it does not exist (used for the EGA-Dev key
// paths given as absolute paths in the environment).
func File(absPath string) (string, error) {
	if _, err := os.Stat(absPath); err != nil {
		return "", fmt.Errorf("file not found: %s: %w", absPath, err)
	}
	return absPath, nil
}

// LoadRootCAPool builds a cert pool from /storage/certs/rootCA.pem, the mkcert
// CA that anchors the broker and database TLS connections.
func LoadRootCAPool() (*x509.CertPool, error) {
	path, err := CertFile("rootCA.pem")
	if err != nil {
		return nil, err
	}
	pem, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	pool := x509.NewCertPool()
	if !pool.AppendCertsFromPEM(pem) {
		return nil, fmt.Errorf("failed to parse rootCA.pem into cert pool")
	}
	return pool, nil
}
