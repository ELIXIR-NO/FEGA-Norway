# Go e2e runner image. Build context is the FEGA-Norway root (compose
# `context: ..`), so paths below are repo-relative — the build needs both the
# e2e module and cli/lega-commander, which live outside this directory.

FROM golang:1.26-alpine AS go-builder
WORKDIR /app

# Layer 1: module graph only — cached until go.mod/go.sum change.
COPY e2e/go.mod e2e/go.sum ./
RUN go mod download

# Layer 2: sources — build the environment binaries (e2e-local, e2e-staging)
# into /out.
COPY e2e/ ./
RUN go build -o /out/ ./cmd/...

# lega-commander CLI: UploadViaLegaCmd shells out to it at the pinned path
# /usr/local/bin/lega-commander.
FROM golang:1.26-alpine AS lega-cmd-builder
WORKDIR /app
COPY cli/lega-commander/ .
RUN go build -o /lega-commander .

FROM alpine:3.21
# No package install needed: every TLS path in the runner anchors on
# /storage/certs/rootCA.pem explicitly (httpx skips verify, pg/amqp build their
# own pools) and lega-commander runs with TLS_SKIP_VERIFY, so the system CA
# bundle is never consulted. busybox sh runs the entrypoint.

# Writable working dir: SetupLocal writes the 10 MiB raw + .enc fixtures to CWD.
WORKDIR /fega-norway

COPY --from=go-builder /out/ /usr/local/bin/
COPY --from=lega-cmd-builder /lega-commander /usr/local/bin/lega-commander
COPY e2e/entrypoint.sh /fega-norway/entrypoint.sh
RUN chmod +x /fega-norway/entrypoint.sh

ENTRYPOINT ["/fega-norway/entrypoint.sh"]
