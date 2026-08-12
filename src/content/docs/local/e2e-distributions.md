---
title: Running the e2e distributions
description: What each end-to-end distribution targets, how to run it, and the configuration each one needs.
---

The Go e2e runner described here lands through pull requests
[#833](https://github.com/ELIXIR-NO/FEGA-Norway/pull/833) and
[#834](https://github.com/ELIXIR-NO/FEGA-Norway/pull/834) and runs ahead of `main`; see
[the note in the dev.sh workflow](../dev-script/) for how this site tracks that state. On
`main` the only runner is the JUnit suite.

## One binary per environment

The Go suite compiles one binary per target environment and the container entrypoint picks the
binary from `E2E_ENV`. There is no runtime variant switch inside a binary: choosing the
environment *is* choosing the program.

| Binary | Target | How it runs | Selector |
| --- | --- | --- | --- |
| `e2e-fega` | the mocked local compose stack | container only | `E2E_ENV=fega` |
| `e2e-egadev` | the live `egadev.uio.no` environment | on a host or in the container | `E2E_ENV=egadev` |
| `e2e-gdi` | nothing yet | placeholder, exits non-zero | `E2E_ENV=gdi` |

`e2e-gdi` prints `the GDI pipeline is not implemented` to stderr and exits 1, so a GDI run can
never be mistaken for a pass. The entrypoint likewise refuses an unknown `E2E_ENV` value with a
non-zero exit.

## Selecting a runner and an environment

Two variables steer the whole thing, both set in `e2e/env.sh` as shell-overridable defaults:

- **`E2E_SUITE`** (`go`, default, or `java`) picks which runner image the `e2e-tests` container
  builds. Everything below that container is shared, so both suites are measured against exactly
  the same stack. The Java suite is retiring; its removal is tracked in
  [#851](https://github.com/ELIXIR-NO/FEGA-Norway/issues/851).
- **`E2E_ENV`** (`fega`, default, `egadev` or `gdi`) picks the Go binary at the container
  entrypoint.

`E2E_TESTS_INTEGRATION` still exists but only steers the Java runner's test-class selection; the
Go runner ignores it.

```bash
./dev.sh start                    # Go runner against the mocked stack
E2E_SUITE=java ./dev.sh start     # the retiring JUnit suite, same stack
```

Switching suites needs a fresh stack (`./dev.sh stop` first): both ingest the same fixture and
assert on archive and inbox state, so one suite's leftovers fail the other for reasons that have
nothing to do with either.

## The fega distribution (mocked stack)

`e2e-fega` runs the full eight-stage FEGA pipeline against the self-contained docker compose
stack: upload via `lega-commander`, ingest, accession, finalize, mapping, inbox cleanup,
release, download. This is what CI runs on every push.

**Setup: none beyond `./dev.sh start`.** `env.sh` supplies a working default for every
`E2E_TESTS_*` variable, and the file-orchestrator stages all crypto material into the certs
directory before the runner is allowed to start: the mkcert root CA that anchors the broker and
database TLS connections, the RSA pair for JWT signing, and the Crypt4GH archive key. The runner
reads that volume directly, so unlike the Java suite it has no `keytool` import step. Key names
it needs (`jwt.priv.pem`, `ega.pub.pem`, `rootCA.pem`) resolve as bare filenames under the certs
directory, which `E2E_TESTS_CERTS_DIR` locates and defaults to the in-container
`/storage/certs`.

**Tokens.** The runner mints its own GA4GH visa, signed with the staged `jwt.priv.pem`, whose
public half the proxy in the stack is configured to trust. Do not set `E2E_TESTS_LSAAI_TOKEN`
here: the upload stage refuses a provided token outside the egadev variant, by design.

**TLS.** The stack serves mkcert certificates the runner does not trust as a system CA, so its
HTTP client skips verification, and `lega-commander` runs with `TLS_SKIP_VERIFY`.

**The result** is the `e2e-tests` container's exit code, which `./dev.sh start` does not wait
for. See [the boot chain](../dev-script/#the-boot-chain) for how to follow the run and read the
verdict.

## The egadev distribution (live staging)

`e2e-egadev` runs the six-stage EGA_DEV pipeline against the live `egadev.uio.no` environment:
upload through the proxy, ingest, accession, mapping, release, and download via a FEGA export
request. It has no finalize and no inbox-cleanup stage.

Unlike the fega distribution it needs real credentials and key material:

| Variable | What it is for |
| --- | --- |
| `E2E_TESTS_LSAAI_TOKEN` | a real LS-AAI access token. It supplies the subject and audience for the visas the runner mints, and is itself sent as the bearer on upload, outbox listing and download |
| `E2E_TESTS_EGA_DEV_BASE_DIRECTORY` | where the 10 MiB test fixture and its `.enc` are created, and where the downloaded file is written (`<base>/out/`) |
| `E2E_TESTS_EGA_DEV_ARCHIVE_PUB_KEYPATH` | the Crypt4GH public key the fixture is encrypted to |
| `E2E_TESTS_EGA_DEV_JWT_PRIV_KEYPATH` | the RSA key that signs the visa carried by the export request |
| `E2E_TESTS_EGA_DEV_JWT_PUB_KEYPATH` | used by the Java runner's export test; no Go stage reads it. [#834](https://github.com/ELIXIR-NO/FEGA-Norway/pull/834) drops the unused Go config field, and the variable itself retires with the Java suite ([#851](https://github.com/ELIXIR-NO/FEGA-Norway/issues/851)) |

The key paths are **absolute** under egadev and resolve anywhere on disk; the fega distribution
instead resolves bare key filenames under the certs directory.

**Running it on a host** is the point of the distribution: export the `E2E_TESTS_*` variables
and run the binary. That means the endpoints as well as the credentials. The `env.sh` defaults
point into the compose stack (the proxy at `proxy`, the CEGA broker at `cegamq:5673`), so a host
run must set the proxy host and port and the CEGA broker connection string to the real egadev
endpoints, values `env.sh` deliberately does not carry, alongside the token and key paths above
(its EGA_DEV block is left commented for the same reason: per-operator values). Two properties
make the host run possible:

- **The EGA_DEV pipeline reads no staged certificates at all.** Its keys come from the absolute
  paths above, the CEGA broker connection verifies against the system roots (the egadev broker
  certificate chains to a public CA that the local stack's mkcert pool cannot verify), the HTTP
  client validates the live certificate, and the direct Postgres check runs only in the FEGA
  pipeline.
- **Where staged certificates *are* read, the directory is configurable.** `E2E_TESTS_CERTS_DIR`
  defaults to the in-container `/storage/certs` and is deliberately not passed through the
  compose template, so a host-side override can never leak into the stack. This knob is
  generality for the FEGA path; the system-roots broker dial is what actually unblocks egadev on
  a host.

Running egadev **inside** the container instead requires wiring the operator does: the EGA_DEV
variables are commented placeholders in the compose template's environment block, and the
`e2e-tests` service mounts no volume through which host-side key files could reach the
container's filesystem.

:::note[What the runner checks about the token, and what it does not]
The runner never verifies the LS-AAI token's signature. It base64url-decodes the payload,
requires non-empty `sub` and `aud` claims, and refuses the run at setup otherwise. Any
well-formed token therefore clears the runner itself; it is the live environment that validates
the token when the runner presents it. That is also why the mocked stack needs no token at all:
there, the runner signs its own visa with a key the stack already trusts. The proxy-side
rejection of forged tokens has its own logic-level unit tests
([#836](https://github.com/ELIXIR-NO/FEGA-Norway/pull/836)); an endpoint-level check against a
running proxy is tracked in [#854](https://github.com/ELIXIR-NO/FEGA-Norway/issues/854).
:::

**Startup validation** arrives with
[#834](https://github.com/ELIXIR-NO/FEGA-Norway/pull/834), which makes a misconfigured run fail
immediately instead of deep inside a stage: as that PR stands (it was written against the
pre-rename runner and is reconciled with the rest on merge), the config is checked right after
loading, every missing variable the selected environment needs is aggregated into a single error
naming them all, ports are range-checked to 1-65535, and the export polling knobs must be
positive integers. The same change trims Go config fields nothing reads, the Java truststore
password among them, while `env.sh` keeps supplying the Java runner's full set until it retires.

One caveat from the source itself: the export request encodes the recipient key as base64 SPKI
DER, and the code marks that encoding as not yet verified against live egadev.

## The gdi distribution

`e2e-gdi` is a deliberate placeholder: selecting `E2E_ENV=gdi` runs a binary that prints
`the GDI pipeline is not implemented` to stderr and exits 1. The Java suite's `GDI` test class
remains the only GDI-flavoured e2e code in the repository, and it retires with the rest of the
JUnit runner.
