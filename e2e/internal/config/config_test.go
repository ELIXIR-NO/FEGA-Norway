package config

import (
	"strings"
	"testing"
)

func validFEGA() *Config {
	return &Config{
		Integration:                    IntegrationFEGA,
		ProxyHost:                      "proxy",
		ProxyPort:                      "443",
		CegaAuthUsername:               "cega",
		CegaAuthPassword:               "pw",
		CegaConnString:                 "amqps://cega",
		ExportRequestMaxRetries:        3,
		ExportRequestIntervalInSeconds: 1,
		SdaDbHost:                      "db",
		SdaDbPort:                      "5432",
		SdaDbUsername:                  "u",
		SdaDbPassword:                  "p",
		SdaDbDatabaseName:              "sda",
		SdaDoaHost:                     "doa",
		SdaDoaPort:                     "8443",
		ProxyTokenAudience:             "aud",
	}
}

func validEgaDev() *Config {
	return &Config{
		Integration:                    IntegrationEgaDev,
		ProxyHost:                      "proxy",
		ProxyPort:                      "443",
		CegaAuthUsername:               "cega",
		CegaAuthPassword:               "pw",
		CegaConnString:                 "amqps://cega",
		ExportRequestMaxRetries:        3,
		ExportRequestIntervalInSeconds: 1,
		LSAAIToken:                     "tok",
		EgaDevBaseDirectory:            "/base",
		EgaDevPubKeyPath:               "/k.pub",
		EgaDevJwtPrivKeyPath:           "/jwt.priv",
		ProxyAdminUsername:             "admin",
		ProxyAdminPassword:             "adminpw",
	}
}

func TestValidateAcceptsCompleteConfig(t *testing.T) {
	if err := validFEGA().Validate(); err != nil {
		t.Errorf("valid FEGA config rejected: %v", err)
	}
	if err := validEgaDev().Validate(); err != nil {
		t.Errorf("valid EGA_DEV config rejected: %v", err)
	}
}

func TestValidateReportsProblems(t *testing.T) {
	tests := []struct {
		name   string
		base   func() *Config
		mutate func(*Config)
		wantIn []string // error must contain each substring; empty means expect no error
	}{
		{
			name:   "unknown integration",
			base:   validFEGA,
			mutate: func(c *Config) { c.Integration = "NOPE" },
			wantIn: []string{"unknown integration"},
		},
		{
			name:   "FEGA missing db host",
			base:   validFEGA,
			mutate: func(c *Config) { c.SdaDbHost = "" },
			wantIn: []string{"E2E_TESTS_SDA_DB_HOST"},
		},
		{
			name:   "FEGA does not require the staging fields",
			base:   validFEGA,
			mutate: func(c *Config) { c.LSAAIToken, c.EgaDevBaseDirectory = "", "" },
			wantIn: nil,
		},
		{
			name:   "EGA_DEV missing token",
			base:   validEgaDev,
			mutate: func(c *Config) { c.LSAAIToken = "" },
			wantIn: []string{"E2E_TESTS_LSAAI_TOKEN"},
		},
		{
			name:   "EGA_DEV does not require the local SDA fields",
			base:   validEgaDev,
			mutate: func(c *Config) { c.SdaDbHost, c.SdaDoaHost = "", "" },
			wantIn: nil,
		},
		{
			name:   "zero retries rejected",
			base:   validFEGA,
			mutate: func(c *Config) { c.ExportRequestMaxRetries = 0 },
			wantIn: []string{"E2E_TESTS_EXPORT_REQUEST_MAX_RETRIES", "positive integer"},
		},
		{
			name:   "aggregates every problem",
			base:   validFEGA,
			mutate: func(c *Config) { c.ProxyHost, c.SdaDoaPort = "", "" },
			wantIn: []string{"E2E_TESTS_PROXY_HOST", "E2E_TESTS_SDA_DOA_PORT"},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := tt.base()
			tt.mutate(c)
			err := c.Validate()
			if len(tt.wantIn) == 0 {
				if err != nil {
					t.Fatalf("expected no error, got %v", err)
				}
				return
			}
			if err == nil {
				t.Fatalf("expected error containing %v, got nil", tt.wantIn)
			}
			for _, sub := range tt.wantIn {
				if !strings.Contains(err.Error(), sub) {
					t.Errorf("error %q does not contain %q", err.Error(), sub)
				}
			}
		})
	}
}
