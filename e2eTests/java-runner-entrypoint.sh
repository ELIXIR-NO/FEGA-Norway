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
    echo "Info: /storage/certs/rootCA.pem not found, skipping certificate import."
fi

# NB! Must be present in the environment.
#
# Choose which test to run based on the E2E_TESTS_INTEGRATION variable.
# If set to "FEGA", run no.elixir.e2eTests.FEGAIntegrationTest.
# Otherwise, run no.elixir.e2eTests.GDIIntegrationTest.
# See the top-level classes in no.elixir.e2eTests for details.
if [ "$E2E_TESTS_INTEGRATION" = "FEGA" ]; then
    echo "Running FEGA integration tests"
    exec java -jar e2eTests.jar execute --select-class no.elixir.e2eTests.FEGAIntegrationTest
elif [ "$E2E_TESTS_INTEGRATION" = "GDI" ]; then
    echo "Running GDI integration tests"
    exec java -jar e2eTests.jar execute --select-class no.elixir.e2eTests.GDIIntegrationTest
elif [ "$E2E_TESTS_INTEGRATION" = "EGA_DEV" ]; then
    echo "Running EGA_DEV integration tests"
    exec java -jar e2eTests.jar execute --select-class no.elixir.e2eTests.EgaDevIntegrationTest
else
    echo "Error: Unknown or unset E2E_TESTS_INTEGRATION value: $E2E_TESTS_INTEGRATION"
    echo "Please set E2E_TESTS_INTEGRATION=FEGA or E2E_TESTS_INTEGRATION=GDI or E2E_TESTS_INTEGRATION=EGA_DEV"
    exit 1
fi
