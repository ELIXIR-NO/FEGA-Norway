#!/bin/bash

source .env
cd certs || exit 1

# Step 1: Generate and install the root
# certificate authority (CA) using mkcert
mkcert -install
echo "CAROOT is $(mkcert -CAROOT)"

# Step 2: Generate SSL/TLS certificates for
# localhost and other services
mkcert localhost db vault mq tsd proxy

# Step 3: Generate the client certificates for
# localhost and other services
mkcert -client localhost db vault mq tsd proxy

# Step 4: Export SSL/TLS certificates and
# private keys to PKCS#12 format
openssl pkcs12 -export \
  -out localhost+5.p12 \
  -in localhost+5.pem \
  -inkey localhost+5-key.pem -passout pass:"${SERVER_CERT_PASSWORD}"
openssl pkcs12 -export \
  -out localhost+5-client.p12 \
  -in localhost+5-client.pem \
  -inkey localhost+5-client-key.pem \
  -passout pass:"${CLIENT_CERT_PASSWORD}"

# Step 5: Convert client key to DER format
openssl pkcs8 -topk8 \
  -inform PEM \
  -in localhost+5-client-key.pem \
  -outform DER \
  -nocrypt \
  -out localhost+5-client-key.der

# Step 6: Generate JWT private and public keys
openssl genpkey -algorithm RSA \
  -out jwt.priv.pem \
  -pkeyopt rsa_keygen_bits:4096
openssl rsa -pubout \
  -in jwt.priv.pem \
  -out jwt.pub.pem

# key, JWT public key, and other secrets
openssl rsa -pubout -in jwt.priv.pem -out jwt.pub.pem
printf "%s" "${KEY_PASSWORD}" >ega.sec.pass
crypt4gh generate -n ega -p ${KEY_PASSWORD}

# Step 8,9: Copy root CA certificate and private key
cp "$(mkcert -CAROOT)/rootCA.pem" rootCA.pem
cp "$(mkcert -CAROOT)/rootCA-key.pem" rootCA-key.pem
chmod 600 rootCA-key.pem

# Step 10: Export root CA certificate to PKCS#12 format
openssl pkcs12 -export \
  -out rootCA.p12 \
  -in rootCA.pem \
  -inkey rootCA-key.pem \
  -passout pass:${ROOT_CERT_PASSWORD}

# Step 11: Rename server and client certificates
cp localhost+5.pem server.pem
cp localhost+5-key.pem server-key.pem
cp localhost+5.p12 server.p12
cp localhost+5-client.pem client.pem
cp localhost+5-client-key.pem client-key.pem
cp localhost+5-client-key.der client-key.der
cp localhost+5-client.p12 client.p12

# Step 12: Move certificates to relevant folders

# tsd
# Note that even though here everything is together,
# in the container it will be something like:
#     /etc/ega/ssl/CA.cert
#     /etc/ega/ssl/server.cert
#     /etc/jwt/public_keys/elixir_aai.pem
# They are just on the same level.
cp rootCA.pem /volumes/tsd-certs/CA.cert &&
  cp server.p12 /volumes/tsd-certs/server.cert &&
  cp jwt.pub.pem /volumes/tsd-certs/elixir_aai.pem

# mq
#   /etc/rabbitmq/ssl/server.pem
#   /etc/rabbitmq/ssl/server-key.pem
#   /etc/rabbitmq/ssl/rootCA.pem
mkdir -p /volumes/mq-confs-and-certs/ssl &&
  cp server.pem /volumes/mq-confs-and-certs/ssl/server.pem &&
  cp server-key.pem /volumes/mq-confs-and-certs/ssl/server-key.pem &&
  cp rootCA.pem /volumes/mq-confs-and-certs/ssl/rootCA.pem

# proxy
mkdir -p /volumes/proxy-certs/ssl/ /volumes/proxy-certs/jwt/ &&
  cp rootCA.p12 /volumes/proxy-certs/ssl/CA.cert &&
  cp server.p12 /volumes/proxy-certs/ssl/server.cert &&
  cp jwt.pub.pem /volumes/proxy-certs/jwt/passport.pem &&
  cp jwt.pub.pem /volumes/proxy-certs/jwt/visa.pem

# db
cp server.pem /volumes/db-certs/pg.pem &&
  cp server-key.pem /volumes/db-certs/pg-server.pem &&
  cp rootCA.pem /volumes/db-certs/CA.pem

# sda (ingest,verify,finalize,mapper,intercept)
#     /etc/ega/
cp rootCA.pem /volumes/sda-certs/CA.cert &&
  cp client.pem /volumes/sda-certs/client.cert &&
  cp client-key.pem /volumes/sda-certs/client-key.cert &&
  cp ega.sec.pem /volumes/sda-certs/ega.sec

# doa
#      /etc/ega/ssl/CA.cert
#      /etc/ega/ssl/client.cert
#      /etc/ega/ssl/client.key
#      /etc/ega/jwt/passport.pem
#      /etc/ega/jwt/visa.pem
#      /etc/ega/crypt4gh/key.pem
#      /etc/ega/crypt4gh/key.pass
mkdir -p /volumes/doa-certs/ssl/ /volumes/doa-certs/jwt/ /volumes/doa-certs/crypt4gh/ &&
  cp rootCA.pem /volumes/doa-certs/ssl/CA.cert &&
  cp client.pem /volumes/doa-certs/ssl/client.cert &&
  cp client-key.der /volumes/doa-certs/ssl/client.key &&
  cp jwt.pub.pem /volumes/doa-certs/jwt/passport.pem &&
  cp jwt.pub.pem /volumes/doa-certs/jwt/visa.pem &&
  cp ega.sec.pem /volumes/doa-certs/crypt4gh/key.pem &&
  cp ega.sec.pass /volumes/doa-certs/crypt4gh/key.pass

# cegamq
# In the container this is where it will be located.
#   /etc/rabbitmq/ssl
cp server.pem /volumes/cegamq-certs/mq.pem &&
  cp server-key.pem /volumes/cegamq-certs/mq-key.pem &&
  cp rootCA.pem /volumes/cegamq-certs/ca.pem

cd .. # Go back to the working directory.

echo "Generated the certificates ✅"
