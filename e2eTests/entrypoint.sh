#!/bin/sh

set -e

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

# Decide which test to run based on INTEGRATION variable
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
