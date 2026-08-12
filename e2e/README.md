# e2e (Go)

The FEGA-Norway end-to-end test suite. It drives the full pipeline (upload,
ingest, accession, finalize, mapping, release, download) against either the
mocked local stack or the live egadev environment.

This directory also owns the **stack** both suites run against:
`docker-compose.template.yml`, `confs/`, `scripts/` and `env.sh`.

## Two runners, one stack

The retiring JUnit suite in [`../e2eTests`](../e2eTests) is kept alongside this
one until the Go suite has proven itself in practice. Only the runner container
differs; every service under test is shared, so a disagreement between the two
is a test-suite difference and never an environment difference.

`E2E_SUITE` picks the runner:

```sh
./dev.sh start                  # go (default)
E2E_SUITE=java ./dev.sh start   # the JUnit suite
```

In CI, pushes always run the Go suite. Run the JUnit one on demand from the
Actions tab: **Build and test** -> Run workflow -> `e2e_suite: java`.

Switching suites needs a **fresh stack** (`./dev.sh stop` first). Both suites
ingest the same fixture and assert on archive and inbox state, so re-running one
over the other's leftovers fails for reasons that have nothing to do with either
suite.

## Layout

Standard `cmd/` + `internal/` layout. The environment selects a **binary**;
there is no runtime variant switch:

| `E2E_ENV` | Binary | Pipeline | Target |
|-----------|--------|----------|--------|
| `fega`    | `e2e-fega`   | full FEGA pipeline | self-contained mocked docker stack (CI); container-only |
| `egadev`  | `e2e-egadev` | EGA_DEV pipeline   | live egadev.uio.no; runs on a host or in the container |
| `gdi`     | `e2e-gdi`    | placeholder, always exits non-zero | GDI (not implemented) |

```
cmd/e2e-fega, cmd/e2e-egadev, cmd/e2e-gdi   # thin mains, one per environment
internal/
  config     # E2E_TESTS_* env -> Config
  constants  # JWT/visa constants + AMQP/HTTP message templates
  state      # the *State threaded through stages + setup/teardown
  check      # assertion helpers (return error instead of throwing)
  common     # random file, checksums, random digits, JSON compaction, waits
  certs      # staged cert material (E2E_TESTS_CERTS_DIR) + TLS pools
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
go build -o build/ ./cmd/...   # builds e2e-fega, e2e-egadev, e2e-gdi
go test ./...                  # unit tests (token key parsing, config)
```

Or via Gradle (the `base`-plugin convention used by the other Go modules):

```sh
./gradlew :e2e:build
./gradlew :e2e:test
```

## Running `e2e-egadev` on a host

`e2e-fega` only makes sense inside the stack, but `e2e-egadev` targets a live
environment and can run straight from a shell: set the `E2E_TESTS_*` variables
(endpoints, the LS-AAI token and the `E2E_TESTS_EGA_DEV_*` key paths, see
`env.sh`) and run the binary. The EGA_DEV pipeline reads its keys from the
absolute `E2E_TESTS_EGA_DEV_*` paths and verifies the broker TLS against the
system roots, so no staged cert material is required. Where staged certs are
read (the FEGA pipeline), their directory comes from `E2E_TESTS_CERTS_DIR`,
defaulting to the in-container `/storage/certs`.

## Scope (Phase 1)

Phase 1 preserves the proven suite behavior unchanged: stage order, the fixed
inter-stage waits, and every assertion. Deferred to Phase 2 (tracked in
`../../docs/e2e-go-rewrite/2026-06-19-proposal.md`): replacing fixed sleeps with
bounded polling and the single-shot finalize DB read.
