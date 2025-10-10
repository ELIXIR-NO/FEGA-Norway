#!/bin/bash

set -e

# Wait until all key subdirectories in /storage are non-empty
for dir in certs confs; do
  echo "Checking $dir..."
  while [ -z "$(ls -A /storage/$dir 2>/dev/null)" ]; do
    echo "Waiting for /storage/$dir to have content..."
    sleep 2
  done
done

# Import the root certificate if it exists
if [ -f "/storage/certs/rootCA.pem" ]; then
    keytool -importcert -file /storage/certs/rootCA.pem \
        -cacerts \
        -alias fega-root-ca \
        -noprompt \
        -storepass changeit
else
    echo "Warning: /storage/certs/rootCA.pem not found, skipping certificate import."
fi

# Choose which test to run based on the INTEGRATION variable.
# If set to "FEGA", run no.elixir.e2eTests.FEGAIntegrationTest.
# Otherwise, run no.elixir.e2eTests.GDIIntegrationTest.
# See the top-level classes in no.elixir.e2eTests for details.
if [ "$INTEGRATION" = "FEGA" ]; then
    echo "Running FEGA integration tests"
    exec java -jar e2eTests.jar --select-class no.elixir.e2eTests.FEGAIntegrationTest
elif [ "$INTEGRATION" = "GDI" ]; then
    echo "Running GDI integration tests"
    exec java -jar e2eTests.jar --select-class no.elixir.e2eTests.GDIIntegrationTest
else
    echo "Error: Unknown or unset INTEGRATION value: $INTEGRATION"
    echo "Please set INTEGRATION=FEGA or INTEGRATION=GDI"
    exit 1
fi
