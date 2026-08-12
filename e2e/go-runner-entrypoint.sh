#!/bin/sh

set -e

# E2E_ENV selects which binary runs; there is no runtime class selector. The
# runner reads its certs from E2E_TESTS_CERTS_DIR (default /storage/certs), so
# no truststore import step is needed.
case "${E2E_ENV}" in
  fega)
    echo "Running FEGA e2e"
    exec e2e-fega
    ;;
  egadev)
    echo "Running EGA_DEV (egadev) e2e"
    exec e2e-egadev
    ;;
  gdi)
    echo "Running GDI e2e"
    exec e2e-gdi
    ;;
  *)
    echo "Error: unknown E2E_ENV value: '${E2E_ENV}' (expected fega|egadev|gdi)" >&2
    exit 1
    ;;
esac
