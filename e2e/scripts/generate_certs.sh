#!/bin/bash

source env.sh

# Note: To run the script you must first source
# the .env config or load the expected env vars
# before executing the script.

cd certs

# Generate and install the root certificate authority.
mkcert -install
echo "CAROOT is $(mkcert -CAROOT)"

# Generate SSL/TLS certificates targeting
# localhost. We specify 6 different hostnames.
mkcert localhost db vault mq tsd proxy cegamq doa

# Generate the client certificates for the services.
mkcert -client localhost db vault mq tsd proxy cegamq doa

# Convert server and client cert to PKCS12 format.
openssl pkcs12 -export \
  -out localhost+7.p12 \
  -in localhost+7.pem \
  -inkey localhost+7-key.pem \
  -passout pass:"${OPENSSL_SERVER_CERT_PASSWORD}"
openssl pkcs12 -export \
  -out localhost+7-client.p12 \
  -in localhost+7-client.pem \
  -inkey localhost+7-client-key.pem \
  -passout pass:"${OPENSSL_CLIENT_CERT_PASSWORD}"

# Convert client key to DER format
openssl pkcs8 -topk8 \
  -inform PEM \
  -in localhost+7-client-key.pem \
  -outform DER \
  -nocrypt \
  -out localhost+7-client-key.der

# Generate private and public JWT keys
openssl genpkey -algorithm RSA \
  -out jwt.priv.pem \
  -pkeyopt rsa_keygen_bits:4096
openssl rsa -pubout \
  -in jwt.priv.pem \
  -out jwt.pub.pem

# key, JWT public key, and other secrets
openssl rsa -pubout -in jwt.priv.pem -out jwt.pub.pem
printf "%s" "${CRYPT4GH_KEY_PASSWORD}" >ega.sec.pass
crypt4gh generate -n ega -p ${CRYPT4GH_KEY_PASSWORD}

# Copy root CA certificate and its private key
cp "$(mkcert -CAROOT)/rootCA.pem" rootCA.pem
cp "$(mkcert -CAROOT)/rootCA-key.pem" rootCA-key.pem
chmod 600 rootCA-key.pem

# Export root CA certificate to PKCS#12 format
openssl pkcs12 -export \
  -out rootCA.p12 \
  -in rootCA.pem \
  -inkey rootCA-key.pem \
  -passout pass:${OPENSSL_ROOT_CERT_PASSWORD}

# Step 11: Rename server and client certificates
cp localhost+7.pem server.pem
cp localhost+7-key.pem server-key.pem
cp localhost+7.p12 server.p12
cp localhost+7-client.pem client.pem
cp localhost+7-client-key.pem client-key.pem
cp localhost+7-client-key.der client-key.der
cp localhost+7-client.p12 client.p12

keytool -importcert -trustcacerts -noprompt \
  -alias fega-root-ca \
  -file rootCA.pem \
  -keystore truststore.p12 \
  -storetype PKCS12 \
  -storepass "${KEYTOOL_TRUSTSTORE_PASSWORD}"

cd .. # Go back to the working directory.

echo "Generated the certificates ✅"
