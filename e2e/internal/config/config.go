// Package config loads the test configuration from E2E_TESTS_* environment
// variables.
//
// The test variant is selected by which binary runs (cmd/e2e-local or
// cmd/e2e-staging): each sets Integration itself rather than reading it from the
// environment. The suite always runs inside the container and reads its certs
// from /storage/certs.
package config

import (
	"os"
	"strconv"
)

// Integration identifies the test variant, which selects the token shape and
// pipeline branching.
const (
	IntegrationFEGA   = "FEGA"
	IntegrationEgaDev = "EGA_DEV"
)

// Config holds the test configuration, populated from the environment.
type Config struct {
	CegaAuthUsername string
	CegaAuthPassword string
	CegaConnString   string

	ProxyHost string
	ProxyPort string

	SdaDbUsername     string
	SdaDbPassword     string
	SdaDbHost         string
	SdaDbPort         string
	SdaDbDatabaseName string

	SdaDoaHost string
	SdaDoaPort string

	TruststorePassword string

	// Integration is set by the binary, not read from the environment.
	Integration string

	ProxyTokenAudience string
	ProxyAdminUsername string
	ProxyAdminPassword string

	ExportRequestMaxRetries        int
	ExportRequestIntervalInSeconds int64

	// LSAAIToken: access token used for upload. If empty, a dummy visa is minted.
	LSAAIToken string
	// TsdProject is a template variable for the ingest/accession messages.
	TsdProject string
	// LSAAISubject is derived from LSAAIToken at staging setup time (mutable).
	LSAAISubject string

	// EGA-Dev (staging) specific.
	EgaDevBaseDirectory  string
	EgaDevPubKeyPath     string
	EgaDevJwtPubKeyPath  string
	EgaDevJwtPrivKeyPath string
}

// Load reads the configuration from the environment. integration is supplied by
// the calling binary (IntegrationFEGA / IntegrationEgaDev).
func Load(integration string) *Config {
	return &Config{
		Integration: integration,

		CegaAuthUsername: os.Getenv("E2E_TESTS_CEGAAUTH_USERNAME"),
		CegaAuthPassword: os.Getenv("E2E_TESTS_CEGAAUTH_PASSWORD"),
		CegaConnString:   os.Getenv("E2E_TESTS_CEGAMQ_CONN_STR"),

		ProxyHost: os.Getenv("E2E_TESTS_PROXY_HOST"),
		ProxyPort: os.Getenv("E2E_TESTS_PROXY_PORT"),

		SdaDbHost:         os.Getenv("E2E_TESTS_SDA_DB_HOST"),
		SdaDbPort:         os.Getenv("E2E_TESTS_SDA_DB_PORT"),
		SdaDbUsername:     os.Getenv("E2E_TESTS_SDA_DB_USERNAME"),
		SdaDbPassword:     os.Getenv("E2E_TESTS_SDA_DB_PASSWORD"),
		SdaDbDatabaseName: os.Getenv("E2E_TESTS_SDA_DB_DATABASE_NAME"),

		SdaDoaHost: os.Getenv("E2E_TESTS_SDA_DOA_HOST"),
		SdaDoaPort: os.Getenv("E2E_TESTS_SDA_DOA_PORT"),

		TruststorePassword: os.Getenv("E2E_TESTS_TRUSTSTORE_PASSWORD"),
		ProxyTokenAudience: os.Getenv("E2E_TESTS_PROXY_TOKEN_AUDIENCE"),
		ProxyAdminUsername: os.Getenv("E2E_TESTS_PROXY_ADMIN_USERNAME"),
		ProxyAdminPassword: os.Getenv("E2E_TESTS_PROXY_ADMIN_PASSWORD"),

		ExportRequestMaxRetries:        atoiOr(os.Getenv("E2E_TESTS_EXPORT_REQUEST_MAX_RETRIES"), 0),
		ExportRequestIntervalInSeconds: atoi64Or(os.Getenv("E2E_TESTS_EXPORT_REQUEST_INTERVAL_IN_SECONDS"), 0),

		LSAAIToken: os.Getenv("E2E_TESTS_LSAAI_TOKEN"),
		TsdProject: os.Getenv("E2E_TESTS_TSD_PROJECT"),
		// LSAAISubject is normally derived from the token during staging setup,
		// which overwrites whatever is read here.
		LSAAISubject: os.Getenv("E2E_TESTS_LSAAI_SUBJECT"),

		EgaDevBaseDirectory:  os.Getenv("E2E_TESTS_EGA_DEV_BASE_DIRECTORY"),
		EgaDevJwtPubKeyPath:  os.Getenv("E2E_TESTS_EGA_DEV_JWT_PUB_KEYPATH"),
		EgaDevJwtPrivKeyPath: os.Getenv("E2E_TESTS_EGA_DEV_JWT_PRIV_KEYPATH"),
		EgaDevPubKeyPath:     os.Getenv("E2E_TESTS_EGA_DEV_ARCHIVE_PUB_KEYPATH"),
	}
}

func atoiOr(s string, def int) int {
	if s == "" {
		return def
	}
	n, err := strconv.Atoi(s)
	if err != nil {
		return def
	}
	return n
}

func atoi64Or(s string, def int64) int64 {
	if s == "" {
		return def
	}
	n, err := strconv.ParseInt(s, 10, 64)
	if err != nil {
		return def
	}
	return n
}
