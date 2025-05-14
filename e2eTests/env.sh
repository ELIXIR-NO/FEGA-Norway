#!/bin/sh


export RUNTIME=container

# Password use to sign the PKCS12 CAROOT.
export OPENSSL_ROOT_CERT_PASSWORD=r00t_cert_passw0rd

# Passwords used to sign all the server & client certificates.
# See e2eTests/scripts/generate_certs.sh
export OPENSSL_SERVER_CERT_PASSWORD=server_cert_passw0rd
export OPENSSL_CLIENT_CERT_PASSWORD=client_cert_passw0rd

# CRYPT4GH key password
export CRYPT4GH_KEY_PASSWORD=key_passw0rd


export PROXY_SSL_ENABLED=true
export PROXY_TSD_SECURE=false
export PROXY_DB_PORT=5432
export PROXY_ROOT_CERT_PASSWORD=
export PROXY_TSD_ROOT_CERT_PASSWORD=
export PROXY_SERVER_CERT_PASSWORD=
export PROXY_CLIENT_ID=
export PROXY_CLIENT_SECRET=
export PROXY_BROKER_HOST=
export PROXY_BROKER_PORT=
export PROXY_BROKER_USERNAME=
export PROXY_BROKER_PASSWORD=
export PROXY_BROKER_VHOST=
export PROXY_BROKER_VALIDATE=
export PROXY_BROKER_SSL_ENABLED=
export PROXY_EXCHANGE=
export PROXY_CEGA_AUTH_URL=
export PROXY_CEGA_USERNAME=
export PROXY_CEGA_PASSWORD=
export PROXY_TSD_HOST=
export PROXY_TSD_ACCESS_KEY=
export PROXY_POSTGRES_PASSWORD=
export PROXY_ADMIN_USER=admin
export PROXY_ADMIN_PASSWORD=aDm!n_01x.
export PROXY_TSD_MQ_HOST=
export PROXY_TSD_MQ_PORT=
export PROXY_TSD_MQ_VHOST=
export PROXY_TSD_MQ_USERNAME=
export PROXY_TSD_MQ_PASSWORD=
export PROXY_TSD_MQ_EXCHANGE=
export PROXY_TSD_MQ_EXPORT_REQUEST_ROUTING_KEY=
export PROXY_TSD_MQ_INBOX_ROUTING_KEY=
export PROXY_TSD_MQ_ENABLE_TLS=
export PROXY_TRUSTSTORE=/etc/ega/store/truststore.p12
export PROXY_TRUSTSTORE_PASSWORD=trustst0re_passw0rd