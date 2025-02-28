#!/bin/bash

source .env

cd certs || exit 1

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

# interceptor
mkdir -p /volumes/interceptor-certs/ &&
  cp rootCA.pem /volumes/interceptor-certs/CA.cert

# mq
#   /etc/rabbitmq/ssl/server.pem
#   /etc/rabbitmq/ssl/server-key.pem
#   /etc/rabbitmq/ssl/rootCA.pem
mkdir -p /volumes/mq-confs-and-certs/ssl &&
  cp server.pem /volumes/mq-confs-and-certs/ssl/server.pem &&
  cp server-key.pem /volumes/mq-confs-and-certs/ssl/server-key.pem &&
  cp rootCA.pem /volumes/mq-confs-and-certs/ssl/rootCA.pem

# proxy
mkdir -p /volumes/proxy-certs/ssl/ /volumes/proxy-certs/jwt/ /volumes/proxy-certs/store/ &&
  cp rootCA.p12 /volumes/proxy-certs/ssl/CA.cert &&
  cp server.p12 /volumes/proxy-certs/ssl/server.p12 &&
  cp jwt.pub.pem /volumes/proxy-certs/jwt/passport.pem &&
  cp jwt.pub.pem /volumes/proxy-certs/jwt/visa.pem &&
  cp truststore.p12 /volumes/proxy-certs/store/truststore.p12
#  cp server_with_fullchain.p12 /volumes/proxy-certs/ssl/server_with_fullchain.p12 &&
#  cp truststore.p12 /volumes/proxy-certs/store/truststore.p12

# db
cp server.pem /volumes/db-certs/pg.pem &&
  cp server-key.pem /volumes/db-certs/pg-server.pem &&
  cp rootCA.pem /volumes/db-certs/CA.pem

# sda (ingest,verify,finalize,mapper,intercept)
#     /etc/ega/
cp rootCA.pem /volumes/sda-certs/CA.cert &&
  cp client.pem /volumes/sda-certs/client.cert &&
  cp client-key.pem /volumes/sda-certs/client-key.cert &&
  cp ega.sec.pem /volumes/sda-certs/ega.sec &&
  mkdir -p /volumes/sda-certs/db &&
  cp client.pem /volumes/db-client-certs/client.cert &&
  cp client-key.pem /volumes/db-client-certs/client-key.cert

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
  cp server.p12 /volumes/doa-certs/ssl/server.p12 &&
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
