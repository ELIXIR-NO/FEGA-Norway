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

exec java -jar e2eTests.jar --select-class no.elixir.e2eTests.IngestionTest
#exec java -jar e2eTests.jar --scan-classpath --include-classname  no.elixir.e2eTests.FEGATestsSuite
