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
- **End-to-end tests.** How to run them outside an IDE, and what to do when they will not run.
  The wiki noted that the only real material lives in comments on a merged pull request,
  [#333](https://github.com/ELIXIR-NO/FEGA-Norway/pull/333).
- **Support.** The wiki's Support section was empty. There is no documented support contact
  other than `fega-norway-support@elixir.no`, which appears only in the signing-key context.

## No end-to-end coverage of GDI

Both runners ship a GDI distribution and neither one tests anything. The Go binary `e2e-gdi`
prints `the GDI pipeline is not implemented` and exits 1. The JUnit `GDIIntegrationTest` is seven
`@Order`-ed methods with empty bodies, so it reports seven passing tests without connecting to
anything.

The difference matters more than the gap: one distribution announces that it is empty, the other
reports a false green. Both are described on
[the e2e distributions page](../../local/e2e-distributions/); the JUnit class retires with the
rest of that module in [#851](https://github.com/ELIXIR-NO/FEGA-Norway/issues/851).

## Verification status of the diagrams

All nine diagrams have been audited stage by stage against the source: the SDA services, the
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

## Pending changes not yet reflected

Some pages carry inline notes about work in flight. As of writing:

- The e2e work is documented **ahead of `main`**, deliberately:
  [the dev.sh workflow](../../local/dev-script/) and
  [the e2e distributions](../../local/e2e-distributions/) describe the integration branch of
  pull request [#833](https://github.com/ELIXIR-NO/FEGA-Norway/pull/833) (the Go `e2e` module
  replacing the Java `e2eTests`), together with
  [#834](https://github.com/ELIXIR-NO/FEGA-Norway/pull/834) (startup config validation) and
  [#836](https://github.com/ELIXIR-NO/FEGA-Norway/pull/836) (proxy token unit tests).
  [The component map](../../start/components/) still describes `main`, where the module is the
  Java `e2eTests` and the release tooling knows nothing of `e2e`.

When those merge, drop the note at the top of the dev.sh page and update the surrounding text.

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
| e2e distributions | The egadev host run needs the LS-AAI token, four key paths and the endpoints | It also needs `E2E_TESTS_CEGAAUTH_USERNAME` and `E2E_TESTS_CEGAAUTH_PASSWORD`. `UploadThroughProxy` sends CEGA HTTP basic auth on both upload `PATCH` calls, and `env.sh` defaults both to the mock value `dummy`, so the run inherits a credential the live environment will not accept rather than failing on a missing one |
| JUnit: FEGA, running it from your IDE | The host run needs only the stack up and two environment variables | It also needs `lega-commander` installed at the hardcoded absolute path `/usr/local/bin/lega-commander`, and the stack's root CA imported into a truststore the host JVM reads. Following the page as published gave four passes and five failures. Both prerequisites are things the container image does at build and start time, which is why they were invisible |
| JUnit: FEGA, `E2E_TESTS_RUNTIME=local` | Redirects the proxy and DOA to `localhost` | Redirects **four** endpoints: it also moves `E2E_TESTS_SDA_DB_HOST` and rewrites `E2E_TESTS_CEGAMQ_CONN_STR`. Those two carry the `FinalizeTest` database assertion and every broker publish |
| JUnit: FEGA, running it from your IDE | `./dev.sh start` "already launches the suite in a container" | It launches the **Go** suite, since `E2E_SUITE` defaults to `go`. The sentence named the wrong runner |
| e2e distributions | Switching suites needs a fresh stack, because "both suites ingest the same fixture and assert on archive and inbox state, so one suite's leftovers fail the other" | The rationale was wrong, and both suites were checked. Each run namespaces itself: the fixture is named from a UUID (`CreateRandomFile` in Go, `UUID.randomUUID()` in `CommonUtils`), the accession id is `EGAF5` plus ten random digits and the dataset id `EGAD` plus eleven, generated the same way on both sides. Every assertion matches on those values rather than counting, so leftovers from an earlier run are invisible to the next one, not fatal to it |

## No way to boot the stack without running the suite

`dev.sh` starts the environment with a bare `docker compose up --build -d` and the compose file
declares no profiles, so the `e2e-tests` service always runs. There is no documented way to bring
up only the services under test.

This matters for the IDE workflow on
[the JUnit FEGA distribution](../../local/e2e/junit-fega/#running-it-from-your-ide): a debugging
session is necessarily a second suite run alongside the container one. The runs do not collide,
per the namespacing above, but the container run still consumes time and log space on every
`./dev.sh start`. A compose profile would fix it.
