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
export PROXY_ROOT_CERT_PASSWORD=$OPENSSL_ROOT_CERT_PASSWORD
export PROXY_TSD_ROOT_CERT_PASSWORD=$OPENSSL_ROOT_CERT_PASSWORD
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


export POSTGRES_CONNECTION=
# export CEGA_MQ_CONNECTION=amqps://<<CEGAMQ_USERNAME>>:<<CEGAMQ_PASSWORD>>@<<CEGAMQ_HOST>>:<<CEGAMQ_PORT>>/<<CEGAMQ_VHOST>>
export CEGA_MQ_EXCHANGE=localega
export CEGA_MQ_QUEUE=v1.files
export LEGA_MQ_CONNECTION=
export LEGA_MQ_EXCHANGE=sda
export LEGA_MQ_QUEUE=files
export ENABLE_TLS=true
export CA_CERT_PATH=/certs/CA.cert

export POSTGRES_POSTGRES_PASSWORD=

export DB_POSTGRES_DB=sda
export DB_PGDATA=/var/lib/postgresql/data
#export DB_POSTGRES_USER=<<SDA_DB_USERNAME>>
#export DB_POSTGRES_PASSWORD=<<SDA_DB_PASSWORD>>
export DB_POSTGRES_SERVER_CERT=/etc/ega/pg.pem
export DB_POSTGRES_SERVER_KEY=/etc/ega/pg-server.pem
export DB_POSTGRES_SERVER_CACERT=/etc/ega/CA.pem
export DB_POSTGRES_VERIFY_PEER=verify-ca

# SDA ---------------------
# Common Variables
export SDA_ARCHIVE_TYPE=posix
export SDA_ARCHIVE_LOCATION=/ega/archive
export SDA_BROKER_HOST=
export SDA_BROKER_PORT=
export SDA_BROKER_USER=
export SDA_BROKER_PASSWORD=
export SDA_BROKER_VHOST=
export SDA_BROKER_EXCHANGE=sda
export SDA_BROKER_ROUTINGERROR=error
export SDA_BROKER_SSL=true
export SDA_BROKER_VERIFYPEER=true
export SDA_BROKER_CACERT=/etc/ega/CA.cert
export SDA_BROKER_CLIENTCERT=/etc/ega/client.cert
export SDA_BROKER_CLIENTKEY=/etc/ega/client-key.cert
export SDA_DB_HOST=
export SDA_DB_PORT=5432
export SDA_DB_USER=
export SDA_DB_PASSWORD=
export SDA_DB_DATABASE=
export SDA_DB_SSLMODE=require
export SDA_DB_CLIENTCERT=/db-client-certs/client.cert
export SDA_DB_CLIENTKEY=/db-client-certs/client-key.cert
export SDA_LOG_LEVEL=debug
export SDA_INBOX_LOCATION=/ega/inbox
export SDA_C4GH_PASSPHRASE=
export SDA_C4GH_FILEPATH=/etc/ega/ega.sec
# Service-specific Variables
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

# DOA ---------------------
export DOA_SSL_MODE=require
export DOA_SSL_ENABLED=true
export DOA_ARCHIVE_PATH=
export DOA_DB_INSTANCE=
export DOA_POSTGRES_USER=
export DOA_POSTGRES_DB=
export DOA_POSTGRES_PASSWORD=
export DOA_OUTBOX_ENABLED=false
export DOA_KEYSTORE_PATH=/etc/ega/ssl/server.p12
export DOA_KEYSTORE_PASSWORD=

# CEGA RabbitMQ ---------------------
export CEGAMQ_RABBITMQ_CONFIG_FILE=/etc/rabbitmq/conf/cega
export CEGAMQ_RABBITMQ_ENABLED_PLUGINS_FILE=/etc/rabbitmq/conf/cega.plugins
export CEGAMQ_RABBITMQ_NODE_PORT=5673

# CEGA Auth ---------------------
export CEGAAUTH_CEGA_USERS_PASSWORD=dummy
export CEGAAUTH_CEGA_USERS_USER=dummy

# Heartbeat ---------------------
# Common Variables
export HEARTBEAT_RABBITMQ_HOST=
export HEARTBEAT_RABBITMQ_PORT=
export HEARTBEAT_RABBITMQ_USER=
export HEARTBEAT_RABBITMQ_PASS=
export HEARTBEAT_RABBITMQ_VHOST=
export HEARTBEAT_RABBITMQ_EXCHANGE=sda
export HEARTBEAT_RABBITMQ_QUEUE=heartbeat
export HEARTBEAT_RABBITMQ_ROUTING_KEY=sda_heartbeat
export HEARTBEAT_RABBITMQ_TLS=true
export HEARTBEAT_RABBITMQ_CA_CERT_PATH=/app/certs/rootCA.pem
# Heartbeat-pub specific
export HEARTBEAT_MODE_PUB=publisher
export HEARTBEAT_PUBLISH_INTERVAL=60
export HEARTBEAT_RABBITMQ_MANAGEMENT_PORT=15671
export HEARTBEAT_PUBLISHER_CONFIG_PATH=/app/publisher_config.json
# Heartbeat-sub specific
export HEARTBEAT_MODE_SUB=subscriber
export HEARTBEAT_REDIS_HOST=redis
export HEARTBEAT_REDIS_PORT=6379
export HEARTBEAT_REDIS_DB=0
