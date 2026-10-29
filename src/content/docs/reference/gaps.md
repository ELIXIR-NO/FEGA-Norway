---
title: Known gaps
description: What this documentation does not yet cover, and which parts are unverified.
---

This page exists so that missing documentation is visible rather than silently absent. If you
know the answer to something here, filling it in is the highest-value contribution you can make
to this site.

## Placeholders inherited from the wiki

These were bare headings with no content in the source wiki. They are listed rather than shipped
as empty sections:

- **Branch naming.** How to name new branches.
- **Secrets.** Where the keys live and how to rotate them, beyond the
  [GPG signing key](../../operations/signing-keys/) which is documented.
- **Support.** The wiki's Support section was empty. There is no documented support contact
  other than `fega-norway-support@elixir.no`, which appears only in the signing-key context.

## No end-to-end coverage of GDI

The GDI distribution tests nothing. The binary `e2e-gdi` prints `the GDI pipeline is not
implemented` and exits 1, so a GDI run announces that it is empty instead of reporting a pass; see
[the e2e distributions page](../../local/e2e-distributions/).

## Verification status of the diagrams

All ten diagrams have been audited stage by stage against the source: the SDA services, the
proxy, Data-Out, `lega-commander`, the end-to-end suite and the CI workflows. Corrections from
that audit are applied.

Two things remain **unverifiable from these repositories**, and are labelled as such on the pages
themselves rather than being asserted:

- **Cancellation entry point.** The claim that cancellation is possible only from the Submission
  portal is not enforced anywhere in this code. Cancel is a message type, and the interceptor
  routes any cancel arriving on the federated queue through to ingest. Whether Central EGA only
  ever emits it from the Submission portal is a CEGA-side fact.
- **Bump-level precedence.** "Highest level wins" for conflicting version markers is the
  documented behaviour of a pinned third-party tagging action, not logic in this repository.

**Message broker wiring.** Exchange names, routing keys and queue names are deliberately not
documented here: they are configuration, they drift per deployment, and the original sketch was
annotated *"incomplete, verify routing keys and queue names"*. Read the SDA configuration.

**The diagrams remain interpretations of a system, not generated from it.** They were redrawn as
Mermaid so they can be version-controlled, themed and diffed rather than depending on image URLs
outside the project's control. Each page links its original sketch. If a diagram and the code
disagree, the code is right and the diagram is a bug.

## Corrections already applied

These differ deliberately from the old wiki, having been checked against the code on `main`:

| Topic | Was said | Actual |
| --- | --- | --- |
| Go version | 1.21 | 1.26.0 in every Go module |
| Required free ports | `5432 5672 5433 80 5673 15672 25672` | `80 5005 5006 5432 5671 5673 6379 8088 8443 10443 15671 15672 25672` |
| Gradle page | General Gradle-versus-Maven comparison | Trimmed to repository-specific content |
| Accession ID | `finalize` assigns it | Central EGA allocates it; `finalize` records it |
| Cancellation cutoff | Dataset release | Mapping into a dataset, which is earlier |
| `lega-commander` | Encrypts the file | Validates that it is already Crypt4GH; never encrypts |
| Upload auth | Visa only | Visa **and** CEGA HTTP Basic, both enforced |
| Upload handshake | Session request, then chunks | First `PATCH` initialises the resumable upload |
| Export request | End user, visa-checked by the proxy | Admin-authed; visa checked later, in Data-Out |
| Export cost | Header-only, no per-recipient copy | Header-only re-encryption, but a full per-recipient copy is staged |
| Download client | `lega-commander` calls Data-Out, verifies checksums, decrypts | It reads the outbox via the proxy and does none of those |
| `e2eTests` bump marker | A valid component | Not in the CI list; the marker fails |
| `FEGA-Norway` releases | Only on path change | Every merged PR, unconditionally |
| `tsd-file-api-client` | Maven Central | GitHub Packages |
| `pre-release-check` | Pure dry run | Genuinely pushes PR-tagged Docker images |
| Why `dev.sh` needs the repository root | It resolves paths relative to its own location | It resolves them relative to the working directory, which is why the root is required |

## Corrections to this site's own pages

Claims this site published and has since corrected against the code. Listed separately from the
wiki table above because these were our errors, not inherited ones.

| Page | Was said | Actual |
| --- | --- | --- |
| e2e distributions | `lega-commander` runs with `TLS_SKIP_VERIFY` | The variable is `LEGA_COMMANDER_TLS_SKIP_VERIFY=true` (`e2e/internal/stages/upload.go`) |
| e2e distributions | The egadev host run needs the LS-AAI token, four key paths and the endpoints | It also needs `E2E_TESTS_CEGAAUTH_USERNAME` and `E2E_TESTS_CEGAAUTH_PASSWORD`. `UploadThroughProxy` sends CEGA HTTP basic auth on both upload `PATCH` calls, so both need values the live environment accepts |
| Go: EGA_DEV, host run | The host run needs the LS-AAI token, the key paths, the endpoints and the CEGA credentials | It also needs `E2E_TESTS_EXPORT_REQUEST_MAX_RETRIES` and `E2E_TESTS_EXPORT_REQUEST_INTERVAL_IN_SECONDS`. The working values live in `env.sh`, which only a container run sources; unset, both fall back to `0`, which startup validation rejects |
| Go: EGA_DEV, host run | An unset CEGA credential sends the mock `dummy`, and an export-request value of 0 skips the outbox poll | Both stop the run at startup instead. `Config.Validate()` requires a non-empty CEGA username and password and a value of at least 1 for each export-request variable, and a host run does not source `env.sh`, so nothing supplies `dummy`. The page contradicted its own note that a misconfigured run fails at startup |
| Go: EGA_DEV, host run | The run needs the LS-AAI token, the key paths, the endpoints, the CEGA credentials and the export-request values | It also needs `E2E_TESTS_PROXY_ADMIN_USERNAME` and `E2E_TESTS_PROXY_ADMIN_PASSWORD`, the basic auth on the export request; `Config.Validate()` requires both for egadev |
| Component map | `lega-commander` is the tool submitters use to encrypt files | It checks that a file is already Crypt4GH and never encrypts. Corrected on 2026-08-19; the wiki made the same claim, listed in the table above |
| Versioning | A bump marker goes "anywhere in a commit message" | Subject lines only. The workflow validates commit **subjects**, so a marker in a body is neither validated nor picked up and the release silently does not happen. The page said both things, 27 lines apart |
| Go: EGA_DEV | Step 1 of the procedure was `E2E_SUITE=go E2E_ENV=egadev ./dev.sh start` | That is the container form, which needs wiring the operator does, and the step said so immediately after giving it. Anyone following the steps in order ran a dead end. The procedure now starts at the host run, and the container command moved into the accordion that answers it |
| Known gaps | "All nine diagrams have been audited" | There are ten mermaid blocks across nine pages: `architecture/export-download` carries two, and the second was uncounted |
| Local stack: the runner | The `e2e-tests` container mounts the TSD inbox read-only, so the suite can assert that the mapper removed the uploaded file | Neither suite read that mount: both check the removal through the proxy's `GET /files?inbox=true` listing (`e2e/internal/stages/inbox.go`). The mount was dropped in [#851](https://github.com/ELIXIR-NO/FEGA-Norway/issues/851) |
| Versioning | `e2e` is "deliberately not a release component", and the path rule covers eight components | `e2e` has been a release component since 2026-08-06, in both `check-commit-message.yml` and `detect-changed-components.yml`, with its own `e2e-<version>` tags; a change under `cli/lega-commander/` releases it too. The path rule covers nine components besides `FEGA-Norway` |
| Workflows, dev script | `build-and-test.yml` runs the end-to-end suite | The file was renamed `unit-and-e2e-test.yml` on 2026-09-02, with its content unchanged |

## No way to boot the stack without running the suite

`dev.sh` starts the environment with a bare `docker compose up --build -d` and the compose file
declares no profiles, so the `e2e-tests` service always runs. There is no documented way to bring
up only the services under test.

This matters whenever you want the stack for something other than a suite run, such as driving
the proxy with `lega-commander` by hand: every `./dev.sh start` runs the full suite as well, which
costs time and log space, and a compose profile would fix it.
