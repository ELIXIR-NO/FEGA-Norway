# e2e (Go)

The FEGA-Norway end-to-end test suite. It drives the full pipeline (upload,
ingest, accession, finalize, mapping, release, download) against either the
mocked local stack or the live egadev environment.

## Layout

Standard `cmd/` + `internal/` layout. The environment selects a **binary**;
there is no runtime variant switch:

| `E2E_ENV` | Binary | Pipeline | Target |
|-----------|--------|----------|--------|
| `local`   | `e2e-local`   | full FEGA pipeline | self-contained mocked docker stack (CI) |
| `staging` | `e2e-staging` | EGA_DEV pipeline   | live egadev.uio.no |

```
cmd/e2e-local, cmd/e2e-staging   # thin mains, one per environment
internal/
  config     # E2E_TESTS_* env -> Config
  constants  # JWT/visa constants + AMQP/HTTP message templates
  state      # the *State threaded through stages + setup/teardown
  check      # assertion helpers (return error instead of throwing)
  common     # random file, checksums, random digits, JSON compaction, waits
  certs      # /storage/certs material + TLS pools
  token      # GA4GH visa JWT mint/forge + RSA key parsing (stdlib; + unit test)
  report     # test output: stage banners, timing, pretty JSON, checks (+ unit test)
  adapters/  # thin wrappers around external libs/protocols
    c4gh     #   Crypt4GH encrypt/decrypt/keys (github.com/neicnordic/crypt4gh)
    amqp     #   CEGA broker publisher (publish-only)
    pg       #   post-finalize Postgres mTLS verification
    httpx    #   TLS-skipping HTTP client for proxy/DOA
  stages     # one function per pipeline stage
  pipeline   # the ordered per-environment sequences, one per binary
```

## Build & test

```sh
go build -o build/ ./cmd/...   # builds e2e-local, e2e-staging
go test ./...                  # unit tests (token key parsing)
```

Or via Gradle (the `base`-plugin convention used by the other Go modules):

```sh
./gradlew :e2e:build
./gradlew :e2e:test
```

## Scope (Phase 1)

Phase 1 preserves the proven suite behavior unchanged: stage order, the fixed
inter-stage waits, and every assertion. Deferred to Phase 2 (tracked in
`../../docs/e2e-go-rewrite/2026-06-19-proposal.md`): replacing fixed sleeps with
bounded polling and the single-shot finalize DB read.
