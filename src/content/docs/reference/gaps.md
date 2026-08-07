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

- Pull request [#833](https://github.com/ELIXIR-NO/FEGA-Norway/pull/833) replaces the Java
  `e2eTests` module with a Go `e2e` module, which affects
  [the component map](../../start/components/) and the compose template path in
  [the dev.sh workflow](../../local/dev-script/).

When those merge, remove the notes and update the surrounding text.

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
