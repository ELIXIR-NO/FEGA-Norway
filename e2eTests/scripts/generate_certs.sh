#!/bin/bash

source .env

# Note: To run the script you must first source
# the .env config or load the expected env vars
# before executing the script.

mkdir -p "tmp_certs" && cd tmp_certs && mkdir -p "bin"

export LOCAL_BIN=./bin
export MKCERT_VERSION="v1.4.4"

curl -fsSL "https://github.com/FiloSottile/mkcert/releases/download/${MKCERT_VERSION}/mkcert-${MKCERT_VERSION}-linux-amd64" \
    -o "${LOCAL_BIN}/mkcert" && \
    chmod +x "${LOCAL_BIN}/mkcert"

curl -fsSL "https://raw.githubusercontent.com/neicnordic/crypt4gh/master/install.sh" \
    | sh -s -- -b "$LOCAL_BIN" && \
    chmod +x "$LOCAL_BIN/crypt4gh"

export mkcert=./bin/mkcert
export crypt4gh=./bin/crypt4gh

# Generate and install the root
# certificate authority.
$mkcert -install
echo "CAROOT is $($mkcert -CAROOT)"

# Generate SSL/TLS certificates targeting
# localhost. We specify 6 different hostnames.
$mkcert localhost db vault mq tsd proxy cegamq

# Generate the client certificates for the services.
$mkcert -client localhost db vault mq tsd proxy cegamq

# Convert server and client cert to PKCS12 format.
openssl pkcs12 -export \
  -out localhost+6.p12 \
  -in localhost+6.pem \
  -inkey localhost+6-key.pem \
  -passout pass:"${SERVER_CERT_PASSWORD}"
openssl pkcs12 -export \
  -out localhost+6-client.p12 \
  -in localhost+6-client.pem \
  -inkey localhost+6-client-key.pem \
  -passout pass:"${CLIENT_CERT_PASSWORD}"

# Convert client key to DER format
openssl pkcs8 -topk8 \
  -inform PEM \
  -in localhost+6-client-key.pem \
  -outform DER \
  -nocrypt \
  -out localhost+6-client-key.der

# Generate private and public JWT keys
openssl genpkey -algorithm RSA \
  -out jwt.priv.pem \
  -pkeyopt rsa_keygen_bits:4096
openssl rsa -pubout \
  -in jwt.priv.pem \
  -out jwt.pub.pem

# key, JWT public key, and other secrets
openssl rsa -pubout -in jwt.priv.pem -out jwt.pub.pem
printf "%s" "${KEY_PASSWORD}" >ega.sec.pass
$crypt4gh generate -n ega -p ${KEY_PASSWORD}

# Copy root CA certificate and its private key
cp "$($mkcert -CAROOT)/rootCA.pem" rootCA.pem
cp "$($mkcert -CAROOT)/rootCA-key.pem" rootCA-key.pem
chmod 600 rootCA-key.pem

# Export root CA certificate to PKCS#12 format
openssl pkcs12 -export \
  -out rootCA.p12 \
  -in rootCA.pem \
  -inkey rootCA-key.pem \
  -passout pass:${ROOT_CERT_PASSWORD}

# Step 11: Rename server and client certificates
cp localhost+6.pem server.pem
cp localhost+6-key.pem server-key.pem
cp localhost+6.p12 server.p12
cp localhost+6-client.pem client.pem
cp localhost+6-client-key.pem client-key.pem
cp localhost+6-client-key.der client-key.der
cp localhost+6-client.p12 client.p12

#cat server.pem $(mkcert -CAROOT)/rootCA.pem > fullchain.pem
#openssl pkcs12 -export \
#  -out server_with_ca.p12 \
#  -inkey server-key.pem \
#  -in fullchain.pem \
#  -name myalias \
#  -password pass:"${SERVER_CERT_PASSWORD}"
#
#keytool -importcert -trustcacerts -noprompt \
#  -alias fega-norway-rootca \
#  -file rootCA.pem \
#  -keystore "truststore.p12" \
#  -storetype PKCS12 \
#  -storepass "changeit"
#
##keytool -importkeystore -srckeystore localhost+6.p12 -srcstoretype PKCS12 -destkeystore keystore.jks -deststoretype JKS -srcstorepass "${SERVER_CERT_PASSWORD}" -deststorepass "${SERVER_CERT_PASSWORD}" -noprompt
##keytool -importcert -trustcacerts -noprompt -alias rootCA -file rootCA.pem -keystore keystore.jks -storepass "${SERVER_CERT_PASSWORD}"

cd .. # Go back to the working directory.

echo "Generated the certificates ✅"
