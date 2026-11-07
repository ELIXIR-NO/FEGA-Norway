package config

import (
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

const (
	envLocalEGAInstanceURL = "LOCAL_EGA_INSTANCE_URL"
	envElixirAAIToken      = "ELIXIR_AAI_TOKEN"
	envCentralEGAUsername  = "CENTRAL_EGA_USERNAME"
	envCentralEGAPassword  = "CENTRAL_EGA_PASSWORD"
	envTSDProxyURL         = "TSD_PROXY_URL"
	envTSDProjName         = "TSD_PROJ_NAME"
	envChunkSize           = "LEGA_COMMANDER_CHUNK_SIZE"
	envTLSSkipVerify       = "LEGA_COMMANDER_TLS_SKIP_VERIFY"
)

const (
	defaultInstanceURL = "https://ega.elixir.no"
	defaultTSDProxyURL = "https://ega.elixir.no"
	defaultTSDProjName = "p969"
	defaultChunkSize   = 50
	minChunkSize       = 1
	// maxChunkSize protects the proxy rather than the client: it reads every
	// chunk fully into heap before handing it on to TSD.
	maxChunkSize = 256
)

// Config is the validated environment for one run.
type Config struct {
	LocalEGAInstanceURL *url.URL
	ElixirAAIToken      string
	CentralEGAUsername  string
	CentralEGAPassword  string
	TSDProxyURL         *url.URL // nil unless TSD was needed
	TSDProjName         string   // empty unless TSD was needed
	ChunkSize           int64    // MiB
	TLSSkipVerify       bool
	// Insecure names the variables whose URL is plain http, so the caller
	// can warn that credentials would travel in clear. It holds variable
	// names, never URLs, since a URL can carry userinfo.
	Insecure []string
}

// ChunkBytes returns the chunk size in bytes.
func (c Config) ChunkBytes() int64 {
	return c.ChunkSize << 20
}

// Need is the set of variable groups a subcommand requires.
type Need uint8

// The variable groups a subcommand can need. Token is ELIXIR_AAI_TOKEN,
// CEGA is both CENTRAL_EGA_* variables, and TSD is TSD_PROXY_URL and
// TSD_PROJ_NAME, which have defaults and so can only be invalid.
const (
	Token Need = 1 << iota
	CEGA
	TSD
)

// Load reads every variable that need asks for, plus the ones every command
// uses, and returns one *Error naming every missing or invalid variable.
func Load(getenv func(string) string, need Need) (Config, error) {
	var l loader
	l.getenv = getenv
	cfg := Config{
		LocalEGAInstanceURL: l.url(envLocalEGAInstanceURL, defaultInstanceURL),
		ChunkSize:           l.chunkSize(),
		TLSSkipVerify:       getenv(envTLSSkipVerify) == "true",
	}
	if need&Token != 0 {
		cfg.ElixirAAIToken = l.required(envElixirAAIToken)
	}
	if need&CEGA != 0 {
		cfg.CentralEGAUsername = l.required(envCentralEGAUsername)
		cfg.CentralEGAPassword = l.required(envCentralEGAPassword)
	}
	if need&TSD != 0 {
		cfg.TSDProxyURL = l.url(envTSDProxyURL, defaultTSDProxyURL)
		cfg.TSDProjName = orDefault(getenv(envTSDProjName), defaultTSDProjName)
	}
	cfg.Insecure = l.insecure
	return cfg, l.err()
}

// loader collects problems across variables so Load can report them all at
// once instead of stopping at the first.
type loader struct {
	getenv   func(string) string
	problems []Problem
	insecure []string
}

func (l *loader) fail(name string, err error) {
	l.problems = append(l.problems, Problem{Var: name, Reason: err.Error()})
}

func (l *loader) required(name string) string {
	v := l.getenv(name)
	if v == "" {
		l.fail(name, errors.New("is not set"))
	}
	return v
}

func (l *loader) url(name, def string) *url.URL {
	u, err := parseURL(orDefault(l.getenv(name), def))
	if err != nil {
		l.fail(name, err)
		return nil
	}
	if u.Scheme == "http" {
		l.insecure = append(l.insecure, name)
	}
	return u
}

func (l *loader) chunkSize() int64 {
	n, err := parseChunkSize(l.getenv(envChunkSize))
	if err != nil {
		l.fail(envChunkSize, err)
	}
	return n
}

// err returns nil, not a nil *Error, when nothing failed, so callers can
// compare the error with nil.
func (l *loader) err() error {
	if len(l.problems) == 0 {
		return nil
	}
	return &Error{Problems: l.problems}
}

func orDefault(v, def string) string {
	if v == "" {
		return def
	}
	return v
}

// parseURL accepts only absolute http and https URLs and drops trailing
// slashes, since callers append paths that start with one. The error never
// quotes the URL, which may carry userinfo.
func parseURL(raw string) (*url.URL, error) {
	u, err := url.Parse(strings.TrimRight(raw, "/"))
	if err != nil || u.Host == "" || (u.Scheme != "http" && u.Scheme != "https") {
		return nil, errors.New("is not an http or https URL")
	}
	return u, nil
}

// parseChunkSize reads a chunk size in MiB, defaulting when raw is empty.
func parseChunkSize(raw string) (int64, error) {
	if raw == "" {
		return defaultChunkSize, nil
	}
	n, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || n < minChunkSize || n > maxChunkSize {
		return 0, fmt.Errorf("must be a whole number of MiB from %d to %d, got %q", minChunkSize, maxChunkSize, raw)
	}
	return n, nil
}
