#!/bin/sh

set -e

# Map E2E_ENV to the matching binary. This is the Go parallel to the Java
# suite's `--select-class` switch: the environment picks which binary runs,
# not a runtime class selector. The Go runner reads /storage/certs directly,
# so there is no JVM truststore import step here.
case "${E2E_ENV:-local}" in
  local)
    echo "Running FEGA (local) e2e"
    exec e2e-local
    ;;
  staging)
    echo "Running EGA_DEV (staging) e2e"
    exec e2e-staging
    ;;
  *)
    echo "Error: unknown E2E_ENV value: '${E2E_ENV}' (expected local|staging)" >&2
    exit 1
    ;;
esac
