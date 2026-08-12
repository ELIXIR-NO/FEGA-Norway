package config

import "testing"

// The unset default must stay /storage/certs: the container relies on it and
// never sets E2E_TESTS_CERTS_DIR.
func TestCertsDirDefault(t *testing.T) {
	t.Setenv("E2E_TESTS_CERTS_DIR", "")
	if got := Load(IntegrationFEGA).CertsDir; got != "/storage/certs" {
		t.Errorf("CertsDir = %q, want /storage/certs", got)
	}
}

func TestCertsDirOverride(t *testing.T) {
	t.Setenv("E2E_TESTS_CERTS_DIR", "/tmp/e2e-certs")
	if got := Load(IntegrationEgaDev).CertsDir; got != "/tmp/e2e-certs" {
		t.Errorf("CertsDir = %q, want /tmp/e2e-certs", got)
	}
}
