# e2e (Go)

The FEGA-Norway end-to-end test suite. It drives the full pipeline (upload,
ingest, accession, finalize, mapping, release, download) against either the
mocked local stack or the live egadev environment.

This directory also owns the **stack** the suite runs against:
`docker-compose.template.yml`, `confs/`, `scripts/` and `env.sh`.

## Running it locally

The host needs Docker with the Compose v2 plugin, and your user has to be able
to run `docker` without sudo; `./dev.sh start` checks both before it builds
anything. Every image, this runner included, is built inside Docker, so the
stack needs no Go or Java on the host. From the repository root:

```sh
./dev.sh start                                           # render the compose file, build and start everything
docker logs -f e2e-tests                                 # follow the run
docker inspect --format='{{.State.ExitCode}}' e2e-tests  # the verdict: 0 is a pass
./dev.sh stop                                            # tear it all down, volumes included
```

`./dev.sh start` returns as soon as the containers are up, long before the
suite finishes, which is why the exit code is the result and the start command
is not. To run the suite again on a stack that is already up,
`./dev.sh reexecute_tests_in_container` rebuilds and restarts only the runner.
The boot order, rebuilding a single service and troubleshooting are covered on
the [docs site](https://elixir-no.github.io/FEGA-Norway/).

`e2e-fega` runs only inside the stack, by design. Its endpoints are in-network
host names, it reads the staged certificates off the shared `storage` volume,
and it shells out to `lega-commander` at the fixed path the image installs it
to. Debugging goes through the container as well: rebuild the service under
test with its `./dev.sh rebuild_and_deploy_*` command, rerun the suite with
`./dev.sh reexecute_tests_in_container` and read `docker logs e2e-tests`.

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

## The published image

The runner ships as a released image,
`ghcr.io/elixir-no/fega-norway:e2e-<version>`. A pull request touching `e2e/`
or `cli/lega-commander/` (the image compiles the CLI into itself) builds
`e2e/Dockerfile` and pushes the result as `e2e-<PR number>`; merging retags
that same image to the version the release job computes. No rebuild happens in
between, so the version tag names exactly the image the pull request built.

Build context is the repository root rather than this directory, because the
image needs `cli/lega-commander` as well as `e2e/`, which is the same reason
the compose service says `context: ..`; a hand build therefore runs from the
root with `-f e2e/Dockerfile`.

`E2E_ENV` picks which binary the entrypoint runs and has no default, so the one
image covers `fega`, `egadev` and `gdi` and refuses to start without it. An
`egadev` run reads the rest from `env.sh`, but three of those variables describe
the filesystem and have to be rewritten for the container.
`E2E_TESTS_EGA_DEV_BASE_DIRECTORY` is where the raw fixture and the `out/`
download get written, so it needs a writable in-container path; the image
WORKDIR `/fega-norway` is one. The two key paths are resolved exactly as given,
so they have to name files inside a mount rather than anything from the host.

```sh
docker run --rm \
  --env-file <your E2E_TESTS_* file> \
  -v <keys dir>:/keys:ro \
  -e E2E_ENV=egadev \
  -e E2E_TESTS_EGA_DEV_BASE_DIRECTORY=/fega-norway \
  -e E2E_TESTS_EGA_DEV_ARCHIVE_PUB_KEYPATH=/keys/archive.pub.pem \
  -e E2E_TESTS_EGA_DEV_JWT_PRIV_KEYPATH=/keys/jwt.priv.pem \
  ghcr.io/elixir-no/fega-norway:e2e-<version>
```

## Running `e2e-egadev` on a host

`e2e-fega` only makes sense inside the stack, but `e2e-egadev` targets a live
environment and can run straight from a shell: set the `E2E_TESTS_*` variables
(endpoints, the CEGA credentials, the proxy admin credentials, the LS-AAI
token and the `E2E_TESTS_EGA_DEV_*` key paths, see `env.sh`) and run the
binary. Set `E2E_TESTS_EXPORT_REQUEST_MAX_RETRIES` and
`E2E_TESTS_EXPORT_REQUEST_INTERVAL_IN_SECONDS` as well, to the 5 and 10 that
`env.sh` uses. The binary refuses to start without a positive value for each,
and it names every missing or invalid variable in a single error. The EGA_DEV
pipeline reads its keys from the absolute `E2E_TESTS_EGA_DEV_*` paths and
verifies the broker TLS against the system roots, so no staged cert material
is required. Where staged certs are read (the FEGA pipeline), their directory
comes from `E2E_TESTS_CERTS_DIR`, defaulting to the in-container
`/storage/certs`.

## Choosing the integration: FEGA, EGA_DEV or GDI

`E2E_ENV` picks a binary, and each binary fixes its integration, so there is no
second knob. The three are not variants of one pipeline: they differ in which
stages run and what they run against.

| | `fega` | `egadev` | `gdi` |
|---|---|---|---|
| Stages | 9 | 6 | none |
| Upload | `lega-commander` | proxy API directly | |
| Negative check | C1 forged visa | not run | |
| Finalize (Postgres mTLS) | yes | not run | |
| Inbox cleanup | yes | not run | |
| Download | DOA, direct | export request | |
| Runs against | mocked stack | live egadev.uio.no | |

`fega` is the one CI runs on every push, and the only one that is self
contained. `egadev` targets a live environment, so it needs real credentials
and skips the stages that depend on stack-internal access. `gdi` is a
placeholder: it prints that the pipeline is not implemented and exits non-zero,
so a scheduled run can never report a false pass.

## Scope (Phase 1)

Phase 1 preserves the proven suite behavior unchanged: stage order, the fixed
inter-stage waits, and every assertion. Deferred to Phase 2 (tracked in
[#866](https://github.com/ELIXIR-NO/FEGA-Norway/issues/866)): replacing fixed
sleeps with bounded polling and the single-shot finalize DB read.
