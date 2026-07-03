#!/bin/sh

set -e

# E2E_ENV selects which binary runs; there is no runtime class selector. The
# runner reads /storage/certs directly, so no truststore import step is needed.
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
