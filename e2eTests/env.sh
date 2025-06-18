#!/bin/sh

# Common config vars
# ---------------------------------------------------------------------------
export OPENSSL_ROOT_CERT_PASSWORD=r00t_cert_passw0rd
export OPENSSL_SERVER_CERT_PASSWORD=server_cert_passw0rd
export OPENSSL_CLIENT_CERT_PASSWORD=client_cert_passw0rd
export KEYTOOL_TRUSTSTORE_PASSWORD=trustst0re_passw0rd
export CRYPT4GH_KEY_PASSWORD=key_passw0rd # CRYPT4GH key password

# CEGAMQ
# ---------------------------------------------------------------------------
export CEGAMQ_HOST=cegamq
export CEGAMQ_PORT=5673
export CEGAMQ_USERNAME=test
export CEGAMQ_PASSWORD=test
export CEGAMQ_VHOST=lega
export CEGAMQ_CONFIG_FILE=/etc/rabbitmq/conf/cega
export CEGAMQ_ENABLED_PLUGINS_FILE=/etc/rabbitmq/conf/cega.plugins
export CEGAMQ_NODE_PORT=5673
export CEGAMQ_CONN_STR="amqps://$CEGAMQ_USERNAME:$CEGAMQ_PASSWORD@$CEGAMQ_HOST:$CEGAMQ_PORT/$CEGAMQ_VHOST"

# CEGAAUTH
# ---------------------------------------------------------------------------
export CEGAAUTH_HOST=cegaauth
export CEGAAUTH_PORT=8443
export CEGAAUTH_URL=http://$CEGAAUTH_HOST:$CEGAAUTH_PORT/username/
export CEGAAUTH_CEGA_USERS_USER=dummy
export CEGAAUTH_CEGA_USERS_PASSWORD=dummy

# TSD
# ---------------------------------------------------------------------------
export TSD_HOST=tsd:8080
export TSD_ACCESS_KEY=s0me_key

# Postgres
# ---------------------------------------------------------------------------
export POSTGRES_HOST=postgres
export POSTGRES_PORT=5432
export POSTGRES_POSTGRES_USER=postgres
export POSTGRES_POSTGRES_PASSWORD=p0stgres_passw0rd
export POSTGRES_POSTGRES_DB=postgres
export POSTGRES_CONN_STR="postgres://$POSTGRES_POSTGRES_USER:$POSTGRES_POSTGRES_PASSWORD@$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_POSTGRES_DB?sslmode=disable"

# TSD RabbitMQ
# ---------------------------------------------------------------------------
export MQ_HOST=mq
export MQ_PORT=5671
export MQ_VHOST=test
export MQ_USER=admin
export MQ_PASSWORD=guest
export MQ_PASSWORD_HASH=4tHURqDiZzypw0NTvoHhpn8/MMgONWonWxgRZ4NXgR8nZRBz
export MQ_SSL_VERIFY=verify_none
export MQ_SSL_FAIL_IF_NO_PEER_CERT=false
export MQ_SSL_DEPTH=2
export MQ_PROTOCOL=amqps
export MQ_CONN_STR="$MQ_PROTOCOL://$MQ_USER:$MQ_PASSWORD@$MQ_HOST:$MQ_PORT/$MQ_VHOST"

# Proxy
# ---------------------------------------------------------------------------
export PROXY_SSL_ENABLED=true
export PROXY_TSD_SECURE=false
export PROXY_DB_PORT=5432
export PROXY_ROOT_CERT_PASSWORD=$OPENSSL_ROOT_CERT_PASSWORD
export PROXY_TSD_ROOT_CERT_PASSWORD=$OPENSSL_ROOT_CERT_PASSWORD
export PROXY_SERVER_CERT_PASSWORD=$OPENSSL_SERVER_CERT_PASSWORD
export PROXY_CLIENT_ID=test
export PROXY_CLIENT_SECRET=test
export PROXY_CEGAAUTH_URL=$CEGAAUTH_URL
export PROXY_CEGAAUTH_USERNAME=$CEGAAUTH_CEGA_USERS_USER
export PROXY_CEGAAUTH_PASSWORD=$CEGAAUTH_CEGA_USERS_PASSWORD
export PROXY_TSD_HOST=$TSD_HOST
export PROXY_TSD_ACCESS_KEY=$TSD_ACCESS_KEY
export PROXY_POSTGRES_PASSWORD=$POSTGRES_POSTGRES_PASSWORD
export PROXY_TSD_MQ_HOST=$MQ_HOST
export PROXY_TSD_MQ_PORT=$MQ_PORT
export PROXY_TSD_MQ_VHOST=$MQ_VHOST
export PROXY_TSD_MQ_USERNAME=$MQ_USER
export PROXY_TSD_MQ_PASSWORD=$MQ_PASSWORD
export PROXY_TSD_MQ_EXCHANGE=sda
export PROXY_TSD_MQ_EXPORT_REQUEST_ROUTING_KEY=exportRequests
export PROXY_TSD_MQ_INBOX_ROUTING_KEY=inbox
export PROXY_TSD_MQ_ENABLE_TLS=true
export PROXY_TRUSTSTORE=/etc/ega/store/truststore.p12
export PROXY_TRUSTSTORE_PASSWORD=$KEYTOOL_TRUSTSTORE_PASSWORD
export PROXY_ADMIN_USER=admin
export PROXY_ADMIN_PASSWORD=aDm!n_01x.
export PROXY_TOKEN_AUDIENCE=test

# Interceptor
# ---------------------------------------------------------------------------
export INTERCEPTOR_POSTGRES_CONNECTION=$POSTGRES_CONN_STR
export INTERCEPTOR_CEGA_MQ_CONNECTION=$CEGAMQ_CONN_STR
export INTERCEPTOR_CEGA_MQ_EXCHANGE=localega
export INTERCEPTOR_CEGA_MQ_QUEUE=v1.files
export INTERCEPTOR_MQ_CONNECTION=$MQ_CONN_STR
export INTERCEPTOR_LEGA_MQ_EXCHANGE=sda
export INTERCEPTOR_LEGA_MQ_QUEUE=files
export INTERCEPTOR_ENABLE_TLS=true
export INTERCEPTOR_CA_CERT_PATH=/certs/CA.cert

# SDA DB
# ---------------------------------------------------------------------------
export DB_HOST=db
export DB_PORT=5432
export DB_POSTGRES_DB=sda
export DB_PGDATA=/var/lib/postgresql/data
export DB_POSTGRES_USER=postgres
export DB_POSTGRES_PASSWORD=ro0tpasswd
export DB_POSTGRES_SERVER_CERT=/etc/ega/pg.pem
export DB_POSTGRES_SERVER_KEY=/etc/ega/pg-server.pem
export DB_POSTGRES_SERVER_CACERT=/etc/ega/CA.pem
export DB_POSTGRES_VERIFY_PEER=verify-ca

# SDA Pipeline
# ---------------------------------------------------------------------------
export SDA_ARCHIVE_TYPE=posix
export SDA_ARCHIVE_LOCATION=/ega/archive
export SDA_BROKER_HOST=$MQ_HOST
export SDA_BROKER_PORT=$MQ_PORT
export SDA_BROKER_USER=$MQ_USER
export SDA_BROKER_PASSWORD=$MQ_PASSWORD
export SDA_BROKER_VHOST=$MQ_VHOST
export SDA_BROKER_EXCHANGE=sda
export SDA_BROKER_ROUTINGERROR=error
export SDA_BROKER_SSL=true
export SDA_BROKER_VERIFYPEER=true
export SDA_BROKER_CACERT=/etc/ega/CA.cert
export SDA_BROKER_CLIENTCERT=/etc/ega/client.cert
export SDA_BROKER_CLIENTKEY=/etc/ega/client-key.cert
export SDA_DB_HOST=$DB_HOST
export SDA_DB_PORT=$DB_PORT
export SDA_DB_USER=$DB_POSTGRES_USER
export SDA_DB_PASSWORD=$DB_POSTGRES_PASSWORD
export SDA_DB_DATABASE=$DB_POSTGRES_DB
export SDA_DB_SSLMODE=require
export SDA_DB_CLIENTCERT=/db-client-certs/client.cert
export SDA_DB_CLIENTKEY=/db-client-certs/client-key.cert
export SDA_LOG_LEVEL=debug
export SDA_INBOX_LOCATION=/ega/inbox
export SDA_C4GH_PASSPHRASE=$CRYPT4GH_KEY_PASSWORD
export SDA_C4GH_FILEPATH=/etc/ega/ega.sec
# Ingest
export SDA_BROKER_QUEUE_INGEST=ingest
export SDA_BROKER_ROUTINGKEY_INGEST=archived
export SDA_INBOX_TYPE=posix
# Verify
export SDA_BROKER_QUEUE_VERIFY=archived
export SDA_BROKER_ROUTINGKEY_VERIFY=verified
# Finalize
export SDA_BROKER_QUEUE_FINALIZE=accessionIDs
export SDA_BROKER_ROUTINGKEY_FINALIZE=completed
# Mapper
export SDA_BROKER_QUEUE_MAPPER=mappings
# Intercept
export SDA_BROKER_QUEUE_INTERCEPT=files

# DOA
# ---------------------------------------------------------------------------
export DOA_SSL_MODE=require
export DOA_SSL_ENABLED=true
export DOA_ARCHIVE_PATH=/ega/archive/
export DOA_DB_INSTANCE=$DB_HOST
export DOA_POSTGRES_USER=$DB_POSTGRES_USER
export DOA_POSTGRES_PASSWORD=$DB_POSTGRES_PASSWORD
export DOA_POSTGRES_DB=$DB_POSTGRES_DB
export DOA_OUTBOX_ENABLED=false
export DOA_KEYSTORE_PATH=/etc/ega/ssl/server.p12
export DOA_KEYSTORE_PASSWORD=$OPENSSL_SERVER_CERT_PASSWORD

# Heartbeat
# ---------------------------------------------------------------------------
export HEARTBEAT_RABBITMQ_HOST=$MQ_HOST
export HEARTBEAT_RABBITMQ_PORT=$MQ_PORT
export HEARTBEAT_RABBITMQ_USER=$MQ_USER
export HEARTBEAT_RABBITMQ_PASS=$MQ_PASSWORD
export HEARTBEAT_RABBITMQ_VHOST=$MQ_VHOST
export HEARTBEAT_RABBITMQ_EXCHANGE=sda
export HEARTBEAT_RABBITMQ_QUEUE=heartbeat
export HEARTBEAT_RABBITMQ_ROUTING_KEY=sda_heartbeat
export HEARTBEAT_RABBITMQ_TLS=true
export HEARTBEAT_RABBITMQ_CA_CERT_PATH=/app/certs/rootCA.pem
# Heartbeat-pub
export HEARTBEAT_MODE_PUB=publisher
export HEARTBEAT_PUBLISH_INTERVAL=60
export HEARTBEAT_RABBITMQ_MANAGEMENT_PORT=15671
export HEARTBEAT_PUBLISHER_CONFIG_PATH=/app/publisher_config.json
# Heartbeat-sub
export HEARTBEAT_MODE_SUB=subscriber
export HEARTBEAT_REDIS_HOST=redis
export HEARTBEAT_REDIS_PORT=6379
export HEARTBEAT_REDIS_DB=0

# E2E Test
# ---------------------------------------------------------------------------
# Determines the runtime mode for the E2E tests.
# If set to "container", the entire test runs inside a Docker container.
# If set to "local", you can run the test using: `./gradlew :e2eTests:test`.
# Mainly used to switch the host/ports and file systems to fetch the certificates.
export E2E_RUNTIME=local

# Helper function to choose value based on runtime
function _runtime_() {
  local container_value=$1
  local local_value=$2

  if [ "$E2E_RUNTIME" = "container" ]; then
    echo "$container_value"
  else
    echo "$local_value"
  fi
}

# Use the function for environment variable assignments
export E2E_PROXY_HOST=$(_runtime_ "proxy" "localhost")
export E2E_PROXY_PORT=$(_runtime_ "8080" "10443")
export E2E_SDA_DOA_HOST=$(_runtime_ "doa" "localhost")
export E2E_SDA_DOA_PORT=$(_runtime_ "8080" "80")
export E2E_CEGAMQ_CONN_STR="amqps://$CEGAMQ_USERNAME:$CEGAMQ_PASSWORD@$(_runtime_ $CEGAMQ_HOST "localhost"):$CEGAMQ_PORT/$CEGAMQ_VHOST"
export E2E_CEGAAUTH_USERNAME=$CEGAAUTH_CEGA_USERS_USER
export E2E_CEGAAUTH_PASSWORD=$CEGAAUTH_CEGA_USERS_PASSWORD
export E2E_SDA_DB_HOST=$(_runtime_ "$DB_HOST" "localhost")
export E2E_SDA_DB_PORT=$DB_PORT
export E2E_SDA_DB_USERNAME=$DB_POSTGRES_USER
export E2E_SDA_DB_PASSWORD=$DB_POSTGRES_PASSWORD
export E2E_SDA_DB_DATABASE_NAME=$DB_POSTGRES_DB
export E2E_TRUSTSTORE_PASSWORD=$KEYTOOL_TRUSTSTORE_PASSWORD
export E2E_PROXY_TOKEN_AUDIENCE=$PROXY_TOKEN_AUDIENCE
export E2E_PROXY_ADMIN_USERNAME=$PROXY_ADMIN_USER
export E2E_PROXY_ADMIN_PASSWORD=$PROXY_ADMIN_PASSWORD
