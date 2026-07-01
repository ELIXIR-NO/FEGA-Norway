// Package state holds the data threaded through the pipeline stages as an
// explicit *State value passed between them (no globals), along with the fixture
// setup and teardown.
package state

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/adapters/c4gh"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/certs"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/common"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/config"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/report"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/token"
)

// fileSize is the 10 MiB test payload size.
const fileSize = 1024 * 1024 * 10

// State carries everything the ordered stages read and write.
type State struct {
	Config *config.Config
	Log    *report.Reporter

	RawFile string // path to the plaintext .raw file
	EncFile string // path to the Crypt4GH-encrypted .enc file

	RawSHA256 string
	EncSHA256 string
	RawMD5    string

	StableID      string // EGAF5... accession id (client-generated)
	DatasetID     string // EGAD... dataset id (client-generated)
	ArchivePath   string // read out of the DB at finalize
	CorrelationID string // AMQP correlation id threaded ingest -> release

	Sender    c4gh.KeyPair
	Recipient c4gh.KeyPair
}

// EncName is the bare filename of the encrypted file (the pipeline key used in
// messages, paths and assertions).
func (s *State) EncName() string { return filepath.Base(s.EncFile) }

// SetupLocal builds the FEGA (local) test fixture: a 10 MiB random file, its
// checksums, a fresh sender keypair, and a Crypt4GH encryption to the archive
// public key staged at /storage/certs/ega.pub.pem.
func SetupLocal(cfg *config.Config, log *report.Reporter) (*State, error) {
	archiveKeyPath, err := certs.CertFile("ega.pub.pem")
	if err != nil {
		return nil, err
	}
	return setup(cfg, log, "./", archiveKeyPath, false)
}

// SetupStaging builds the EGA-Dev (staging) fixture: same payload, but the
// subject is derived from the real LS-AAI token, both sender and recipient
// keypairs are generated, and the file is encrypted to the EGA-Dev archive
// public key given by an absolute path.
func SetupStaging(cfg *config.Config, log *report.Reporter) (*State, error) {
	sub, _, err := token.ExtractLSAAIDetails(cfg.LSAAIToken)
	if err != nil {
		return nil, fmt.Errorf("extracting subject from LS-AAI token: %w", err)
	}
	cfg.LSAAISubject = sub

	archiveKeyPath, err := certs.File(cfg.EgaDevPubKeyPath)
	if err != nil {
		return nil, err
	}
	return setup(cfg, log, cfg.EgaDevBaseDirectory, archiveKeyPath, true)
}

func setup(cfg *config.Config, log *report.Reporter, basePath, archiveKeyPath string, genRecipient bool) (*State, error) {
	s := &State{Config: cfg, Log: log}

	log.Info("generating random test file", "bytes", fileSize)
	rawFile, err := common.CreateRandomFile(basePath, fileSize)
	if err != nil {
		return nil, fmt.Errorf("creating random file: %w", err)
	}
	s.RawFile = rawFile

	if s.RawSHA256, err = common.Sha256Hex(rawFile); err != nil {
		return nil, err
	}
	if s.RawMD5, err = common.Md5Hex(rawFile); err != nil {
		return nil, err
	}
	log.Info("raw checksums", "sha256", s.RawSHA256, "md5", s.RawMD5)

	if s.Sender, err = c4gh.GenerateKeyPair(); err != nil {
		return nil, fmt.Errorf("generating sender keypair: %w", err)
	}
	if genRecipient {
		if s.Recipient, err = c4gh.GenerateKeyPair(); err != nil {
			return nil, fmt.Errorf("generating recipient keypair: %w", err)
		}
	}

	archivePub, err := c4gh.ReadPublicKeyFile(archiveKeyPath)
	if err != nil {
		return nil, fmt.Errorf("reading archive public key %s: %w", archiveKeyPath, err)
	}

	s.EncFile = filepath.Join(basePath, filepath.Base(rawFile)+".enc")
	log.Info("encrypting file with Crypt4GH", "out", s.EncName())
	if err := c4gh.Encrypt(s.EncFile, rawFile, s.Sender.Private, archivePub); err != nil {
		return nil, fmt.Errorf("encrypting file: %w", err)
	}

	if s.EncSHA256, err = common.Sha256Hex(s.EncFile); err != nil {
		return nil, err
	}
	log.Info("encrypted file checksum", "sha256", s.EncSHA256)

	return s, nil
}

// Cleanup removes the temporary raw and encrypted files.
func (s *State) Cleanup() error {
	var firstErr error
	for _, p := range []string{s.RawFile, s.EncFile} {
		if p == "" {
			continue
		}
		if err := os.Remove(p); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	return firstErr
}
