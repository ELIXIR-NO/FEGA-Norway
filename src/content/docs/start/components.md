---
title: The component map
description: Every releasable component in the FEGA-Norway monorepo, what it does, and what language it is written in.
---

The monorepo is split into libraries (`lib/`), services (`services/`), a command-line client
(`cli/`) and the end-to-end test suite. Each of the components below is independently
versioned and released; see [versioning and releases](../../contributing/versioning/) for how
that is triggered.

## Libraries

Published to Maven Central, consumed by the services here and usable on their own.

| Component | Language | What it does |
| --- | --- | --- |
| `crypt4gh` | Java | Implementation of the Crypt4GH container format: header packets, X25519 key agreement, ChaCha20-Poly1305 segment encryption. |
| `clearinghouse` | Java | Validates GA4GH Passports and Visas: signature verification, JWKS retrieval, expiry and claim checks. This is what decides whether a token grants access. |
| `tsd-file-api-client` | Java | Typed client for UiO TSD's file API, including the resumable upload protocol. |

## Services

| Component | Language | What it does |
| --- | --- | --- |
| `localega-tsd-proxy` | Java (Spring Boot) | The front door. Authenticates the submitter against Life Science AAI or CEGA, then streams uploads through into TSD. |
| `mq-interceptor` | Go | Bridges the CEGA message broker and the local one, translating and validating messages in both directions. |
| `tsd-api-mock` | Java | Stand-in for the real TSD file API so the stack can run end to end locally. Test infrastructure, not production. |
| `cega-mock` | Go | Stand-in for Central EGA's authentication and broker endpoints. Test infrastructure, not production. |

## Client

| Component | Language | What it does |
| --- | --- | --- |
| `lega-commander` | Go | The command-line tool submitters use to upload, list and download files. It checks a file is already Crypt4GH rather than encrypting it. |

## Test suite

| Component | Language | What it does |
| --- | --- | --- |
| `e2e` | Go | Drives the whole pipeline against a running stack and asserts each stage. Picks its target environment with `E2E_ENV`. |
| `e2eTests` | Java (JUnit) | The retiring JUnit runner, kept while both coexist. One test class per environment. |

:::note[Two runners, for now]
Both are on `main` and both are tracked as release components. `E2E_SUITE` picks which one runs,
defaulting to `e2e`. The JUnit module is being removed in
[#851](https://github.com/ELIXIR-NO/FEGA-Norway/issues/851); until then a change under
`e2eTests/` still cuts a release for it.
:::

## The repository itself

`FEGA-Norway` is also a release component in its own right, covering changes that are not
scoped to any single module.

## Where the rest of the system lives

FEGA Norway is not self-contained. Two external codebases complete the picture, and you will
end up reading both:

- **[neicnordic/sensitive-data-archive](https://github.com/neicnordic/sensitive-data-archive)**
  (SDA) supplies the archive pipeline itself: the ingest, verify, finalize and mapper services,
  plus the Data-Out download APIs. FEGA Norway runs these as stock upstream images, **deployed
  inside UiO TSD**.
- **[unioslo/tsd-file-api](https://github.com/unioslo/tsd-file-api)** is the real UiO TSD file
  API that the proxy uploads into. `tsd-api-mock` stands in for it locally.

The containment matters: the proxy is the only component outside TSD. Once bytes cross that
boundary on upload, the entire archive pipeline, its storage and its download APIs all run within
it.

See the [system overview](../../architecture/system/) for how the three fit together.
