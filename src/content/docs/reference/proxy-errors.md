---
title: Proxy error contract
description: The status, problem body and stable code the proxy answers each failure with, as introduced by PR 933.
---

The error contract is how `localega-tsd-proxy` tells a client what went wrong: an HTTP status that
means something, and a problem document carrying a stable `code` that clients switch on instead of
parsing message text. It is introduced by
[ELIXIR-NO/FEGA-Norway#933](https://github.com/ELIXIR-NO/FEGA-Norway/pull/933), which implements
parts (a) and (b) of [#913](https://github.com/ELIXIR-NO/FEGA-Norway/issues/913).

:::caution[Not yet merged]
This page describes the behaviour #933 brings, not the proxy on `main` today. On `main`, most
failures answer `403`, either with no body or with the Java exception text as the body. A missing
`Proxy-Authorization` header is Spring's `400` on every file endpoint except the upload notify,
which answers `401`. The statuses on the [upload](../../architecture/upload/) and
[export and download](../../architecture/export-download/) pages describe `main`.
:::

## The problem document

Every failure is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem document with the
media type `application/problem+json`. It carries `title`, `status`, `detail` and `instance` (the
request path), plus two top-level members of its own:

- **`code`** is the stable name from the table below. A code is never renamed once released.
- **`requestId`** is an id the proxy generates for each request. Every proxy log line for that
  request carries the same id.

The proxy sets no `type`, so the member is absent, which RFC 9457 reads as `about:blank`. An
invalid LS AAI token sent in `Proxy-Authorization` looks like this:

```text
HTTP/1.1 401 Unauthorized
Content-Type: application/problem+json
WWW-Authenticate: Bearer error="invalid_token"

{
  "title": "Invalid LS AAI token",
  "status": 401,
  "detail": "The LS AAI token is invalid, expired or not issued for this service.",
  "instance": "/files",
  "code": "ELIXIR_AAI_TOKEN_INVALID",
  "requestId": "6f1d2c3a-8b4e-4f0a-9c7d-2e5b1a0f9d84"
}
```

The `WWW-Authenticate` challenge appears only on `ELIXIR_AAI_TOKEN_INVALID`, and only when the
token arrived in `Proxy-Authorization`. No failure carries a Basic challenge, so a browser calling
`/export` never pops its native login prompt.

## Fixed detail, and the request id

The `detail` is fixed text per code. Exception messages, TSD's answers and Central EGA's text go
to the server log only and never reach a client. #933's tests plant sentinel text in CEGA and TSD
failures and assert it never appears in a response body.

What a user can report is the `requestId`. Support finds the matching log line by that id, which
holds the real cause.

## Codes

| Code | Status | When | Detail |
| --- | --- | --- | --- |
| `MISSING_CREDENTIALS` | 401 | A required credential is absent: the `Proxy-Authorization` header on a file endpoint, the EGA Basic header where one is required, or the admin Basic header on `/export`. | The request lacks the credentials this endpoint requires. |
| `ELIXIR_AAI_TOKEN_INVALID` | 401 | The LS AAI token is malformed, expired, forged, issued for another audience, has no subject, or is signed with a key the issuer does not publish. Its passport or visas failing verification counts too. On `/export/gdi`, the token in the request body fails the same checks. | The LS AAI token is invalid, expired or not issued for this service. |
| `EGA_AUTH_FAILED` | 401 | The EGA username is unknown, the password is wrong, or the Basic header cannot be decoded. All three give the same answer. | The EGA username or password is wrong. |
| `ADMIN_AUTH_FAILED` | 401 | The admin username or password on `/export` is wrong. | The admin username or password is wrong. |
| `VISA_DENIED` | 403 | On `/export/gdi`, the token carries no valid visa, or none of its visas grants the requested dataset or file. | No valid visa grants access to the requested resource. |
| `ACCOUNT_MAPPING_CONFLICT` | 403 | On an upload or an upload notify, the EGA account is already linked to a different LS AAI account. | This EGA account is already linked to a different LS AAI account. |
| `FILE_NOT_FOUND` | 404 | A download or a delete names a file TSD does not have. | The requested file does not exist. |
| `FOLDER_NOT_FOUND` | 404 | TSD answers a listing with 404 because the user's folder does not exist. Both the inbox and the outbox listing share this check. | The folder does not exist yet; the first upload creates it. |
| `INVALID_REQUEST` | 400 | The request is malformed: a chunk without `md5`, a chunk number that is not a number, a bad `fileName`, `fileSize` or `sha256` on notify, or an export body that is missing, lacks its token or fails schema validation. | The request is malformed or misses a required parameter. |
| `CHECKSUM_MISMATCH` | 422 | An uploaded chunk does not match its MD5. The proxy discards the resumable upload. | The uploaded data does not match its MD5 checksum, so the upload cannot be resumed; upload the whole file again. |
| `CEGA_UNAVAILABLE` | 503 | Central EGA could not answer the credentials lookup: it is down, timed out, or answered anything other than its unknown-user `400`. | Central EGA could not be reached to check the EGA credentials; try again later. |
| `UPSTREAM_FAILED` | 502 | A dependency failed: a TSD call (token exchange, listing, upload, download, delete, resumables), a RabbitMQ publish, or an LS AAI or visa issuer that cannot be reached (OpenID configuration, key set, userinfo). | A service the proxy depends on failed; try again later. |
| `INTERNAL` | 500 | Anything else. | The proxy failed to handle the request. |

Requests that Spring rejects before any handler runs keep Spring's own status and title. A missing
required query parameter is one, an unsupported method another. They still carry `INVALID_REQUEST`
for a 4xx or `INTERNAL` for a 5xx, along with that code's fixed detail and the `requestId`. So
`INVALID_REQUEST` is usually a 400, but not always.

Required query parameters resolve before authentication. A malformed request therefore answers
`400` without any credentials being checked, and nothing runs.

## Credentials per endpoint

| Endpoint | LS AAI token in `Proxy-Authorization` | EGA Basic in `Authorization` | Admin Basic in `Authorization` |
| --- | --- | --- | --- |
| Upload: `PATCH /stream/{fileName}` and `POST /stream/{fileName}/notify` | Required | Required | No |
| Inbox listing: `GET /files` | Required | Required | No |
| Outbox listing: `GET /files?inbox=false` | Required | No | No |
| Download: `GET /stream/{fileName}` | Required | No | No |
| Delete: `DELETE /files` | Required | Required | No |
| Resumables: `GET` and `DELETE /resumables` | Required | Required | No |
| Export: `POST /export/fega` and `POST /export/gdi` | No | No | Required |

Every file endpoint needs both checks unless it is explicitly exempted, which only the outbox
listing and the download are. A new endpoint therefore cannot ship without EGA authentication by
omission. The outbox listing matches `inbox=false` exactly, and any other value is the inbox
listing.

`/export/gdi` takes the LS AAI token in its JSON body, not in a header, which is why its
`ELIXIR_AAI_TOKEN_INVALID` carries no challenge.

## What clients should do

- **Switch on `code`.** It is the stable part of the contract. Do not match on `title` or
  `detail`.
- **Fall back to `status`** for a code you do not know, or for an older proxy that sends none.
- **Retry only on `502` and `503`.** Every other failure will fail the same way again.
- **Treat `FOLDER_NOT_FOUND` on an inbox listing as "not created yet".** The first upload creates
  the folder, so a new user's listing is not an outage.
- **Expect a dropped connection, not a problem document,** when a download fails after its first
  bytes. The `200` has already gone out, so the proxy drops the connection rather than let a
  truncated file look complete.
- **Show the `requestId`** with any error, so the user can quote it to support.

`lega-commander` on `main` does not read `code` yet. Switching on it is part (c) of #913, which
lands in the new `cli/lega` module of
[#932](https://github.com/ELIXIR-NO/FEGA-Norway/pull/932).

The `tsd-api-mock` used by the local stack answers a missing folder with `200` and an empty list,
so the local e2e stack never sees `FOLDER_NOT_FOUND`.
