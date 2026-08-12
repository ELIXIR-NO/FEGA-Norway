// Package certs locates the TLS/crypto material the suite needs: files staged
// under the configured certs directory (config.Config.CertsDir) plus absolute
// key paths given in the environment. It reads nothing from the environment
// itself; callers pass the directory in.
package certs

import (
	"crypto/x509"
	"fmt"
	"os"
	"path/filepath"
)

// CertFile returns the path to name under dir, erroring if it does not exist.
func CertFile(dir, name string) (string, error) {
	return File(filepath.Join(dir, name))
}

// File returns absPath, erroring if it does not exist (used for the EGA-Dev key
// paths given as absolute paths in the environment).
func File(absPath string) (string, error) {
	if _, err := os.Stat(absPath); err != nil {
		return "", fmt.Errorf("file not found: %s: %w", absPath, err)
	}
	return absPath, nil
}

// LoadRootCAPool builds a cert pool from rootCA.pem under dir, the mkcert CA
// that anchors the local stack's broker and database TLS connections.
func LoadRootCAPool(dir string) (*x509.CertPool, error) {
	path, err := CertFile(dir, "rootCA.pem")
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
