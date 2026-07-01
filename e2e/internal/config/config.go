// Package config loads the test configuration from E2E_TESTS_* environment
// variables.
//
// The test variant is selected by which binary runs (cmd/e2e-local or
// cmd/e2e-staging): each sets Integration itself rather than reading it from the
// environment. The suite always runs inside the container and reads its certs
// from /storage/certs.
package config

import (
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"
)

// Integration identifies the test variant, which selects the token shape and
// pipeline branching.
type Integration string

const (
	IntegrationFEGA   Integration = "FEGA"
	IntegrationEgaDev Integration = "EGA_DEV"
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
	Integration Integration

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
// the calling binary (IntegrationFEGA / IntegrationEgaDev). Load only reads;
// call Validate to check the result before use.
func Load(integration Integration) *Config {
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

// Validate checks that the environment supplied every field the selected
// variant actually needs, aggregating all problems into a single error. Calling
// it right after Load makes a misconfigured run fail immediately at startup with
// a clear list of what is missing, instead of dying deep in a stage on an empty
// host or a silently-zeroed retry count.
func (c *Config) Validate() error {
	if c.Integration != IntegrationFEGA && c.Integration != IntegrationEgaDev {
		return fmt.Errorf("unknown integration %q (want %q or %q)",
			c.Integration, IntegrationFEGA, IntegrationEgaDev)
	}

	var problems []string
	req := func(envName, value string) {
		if value == "" {
			problems = append(problems, envName)
		}
	}
	positive := func(envName string, value int64) {
		if value <= 0 {
			problems = append(problems, envName+" (must be a positive integer)")
		}
	}

	// Needed by both variants: the proxy endpoint, CEGA credentials and broker,
	// and the inbox/outbox polling knobs (a zero retry count means the poll loop
	// never runs).
	req("E2E_TESTS_PROXY_HOST", c.ProxyHost)
	req("E2E_TESTS_PROXY_PORT", c.ProxyPort)
	req("E2E_TESTS_CEGAAUTH_USERNAME", c.CegaAuthUsername)
	req("E2E_TESTS_CEGAAUTH_PASSWORD", c.CegaAuthPassword)
	req("E2E_TESTS_CEGAMQ_CONN_STR", c.CegaConnString)
	positive("E2E_TESTS_EXPORT_REQUEST_MAX_RETRIES", int64(c.ExportRequestMaxRetries))
	positive("E2E_TESTS_EXPORT_REQUEST_INTERVAL_IN_SECONDS", c.ExportRequestIntervalInSeconds)

	switch c.Integration {
	case IntegrationFEGA:
		// Local drives the SDA database directly (finalize) and the DOA
		// (download), and mints its own visa against the proxy audience.
		req("E2E_TESTS_SDA_DB_HOST", c.SdaDbHost)
		req("E2E_TESTS_SDA_DB_PORT", c.SdaDbPort)
		req("E2E_TESTS_SDA_DB_USERNAME", c.SdaDbUsername)
		req("E2E_TESTS_SDA_DB_PASSWORD", c.SdaDbPassword)
		req("E2E_TESTS_SDA_DB_DATABASE_NAME", c.SdaDbDatabaseName)
		req("E2E_TESTS_SDA_DOA_HOST", c.SdaDoaHost)
		req("E2E_TESTS_SDA_DOA_PORT", c.SdaDoaPort)
		req("E2E_TESTS_PROXY_TOKEN_AUDIENCE", c.ProxyTokenAudience)
	case IntegrationEgaDev:
		// Staging authenticates with a real LS-AAI token, encrypts to the egadev
		// archive key, signs visas with the egadev key, and drives the export
		// endpoint with the proxy-admin credentials.
		req("E2E_TESTS_LSAAI_TOKEN", c.LSAAIToken)
		req("E2E_TESTS_EGA_DEV_BASE_DIRECTORY", c.EgaDevBaseDirectory)
		req("E2E_TESTS_EGA_DEV_ARCHIVE_PUB_KEYPATH", c.EgaDevPubKeyPath)
		req("E2E_TESTS_EGA_DEV_JWT_PRIV_KEYPATH", c.EgaDevJwtPrivKeyPath)
		req("E2E_TESTS_PROXY_ADMIN_USERNAME", c.ProxyAdminUsername)
		req("E2E_TESTS_PROXY_ADMIN_PASSWORD", c.ProxyAdminPassword)
	}

	if len(problems) > 0 {
		sort.Strings(problems)
		return fmt.Errorf("invalid %s config: %s", c.Integration, strings.Join(problems, ", "))
	}
	return nil
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
