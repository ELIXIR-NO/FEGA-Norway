#!/bin/bash

# Find the absolute path of this script directory (self)
E2E_DIR="$(dirname -- "${BASH_SOURCE[0]}")" # Relative
E2E_DIR="$(cd -- "$E2E_DIR" && pwd)" # Absolute
if [[ -z "$E2E_DIR" ]] ; then
  # error; for some reason, the path is not accessible
  exit 1 # fail
fi

export E2E_DIR
export CONFS_DIR="$E2E_DIR/confs"
export LOCAL_BIN="$E2E_DIR/bin"
export TMP_DIR="$E2E_DIR/tmp"

export TMP_CERTS_DIR="$TMP_DIR/certs"
export TMP_VOLUMES_DIR="$TMP_DIR/volumes"
export TMP_CONFS_DIR="$TMP_DIR/confs"

export SERVER_CERT_PASSWORD=server_cert_passw0rd
export CLIENT_CERT_PASSWORD=client_cert_passw0rd
export ROOT_CERT_PASSWORD=r00t_cert_passw0rd
export KEY_PASSWORD=key_passw0rd # Also used by SDA

export CEGA_AUTH_URL=http://cegaauth:8443/lega/v1/legas/users/
export CEGA_USERNAME=dummy
export CEGA_PASSWORD=dummy
export CEGA_MQ_CONNECTION=amqps://test:test@cegamq:5672/lega?cacertfile=/etc/ega/ssl/CA.cert

export EGA_BOX_USERNAME=dummy # Used by IngestionTest.java
export EGA_BOX_PASSWORD=dummy # Used by IngestionTest.java

export BROKER_HOST=cegamq
export BROKER_PORT=5672
export BROKER_USERNAME=test
export BROKER_PASSWORD=test
export BROKER_VHOST=lega
export BROKER_VALIDATE=false

export EXCHANGE=localega

export TSD_ROOT_CERT_PASSWORD=r00t_cert_passw0rd
export TSD_HOST=tsd:8080
export TSD_PROJECT=p11
export TSD_ACCESS_KEY=s0me_key

export DB_HOST=db
export DB_DATABASE_NAME=lega

export DB_LEGA_IN_USER=lega_in
export DB_LEGA_IN_PASSWORD=in_passw0rd # Also used by IngestionTest.java
export DB_LEGA_OUT_USER=lega_out
export DB_LEGA_OUT_PASSWORD=0ut_passw0rd

export PRIVATE_BROKER_VHOST=test     # Also used by SDA
export PRIVATE_BROKER_USER=admin     # Also used by SDA
export PRIVATE_BROKER_PASSWORD=guest # Also used by SDA
export PRIVATE_BROKER_HASH=4tHURqDiZzypw0NTvoHhpn8/MMgONWonWxgRZ4NXgR8nZRBz

export PUBLIC_BROKER_USER=admin
export PUBLIC_BROKER_PASSWORD=guest
export PUBLIC_BROKER_HASH=4tHURqDiZzypw0NTvoHhpn8/MMgONWonWxgRZ4NXgR8nZRBz

export ARCHIVE_PATH=/ega/archive/

export MQ_HOST=mq
export MQ_CONNECTION=amqps://admin:guest@mq:5671/test
export DB_IN_CONNECTION=postgres://lega_in:in_passw0rd@db:5432/lega?application_name=LocalEGA
export DB_OUT_CONNECTION=postgres://lega_out:0ut_passw0rd@db:5432/lega?application_name=LocalEGA
export POSTGRES_PASSWORD=p0stgres_passw0rd
export POSTGRES_CONNECTION=postgres://postgres:p0stgres_passw0rd@postgres:5432/postgres?sslmode=disable



export NEIC_IMAGE_TAG=ghcr.io/neicnordic/sensitive-data-archive:sha-5334501063d8cdd62594fcc045914f501a7a4026
export NEIC_SDA_STACK_IMAGE="$NEIC_IMAGE_TAG"
export NEIC_SDA_POSTGRES_IMAGE="$NEIC_IMAGE_TAG-postgres"

export LOCAL_NETWORK_NAME=lega

function create_lega_network_if_not_exists() {
    local network_name=$LOCAL_NETWORK_NAME
    local network_exists=$(docker network ls --filter name=${network_name} --format '{{.Name}}')
    if [ "${network_exists}" == "${network_name}" ]; then
        echo "Docker network '${network_name}' already exists."
    else
        echo "Creating Docker network '${network_name}'..."
        docker network create ${network_name}
        echo "Docker network '${network_name}' created."
    fi
}


function build_and_run_tsd() {
  docker rm tsd
  docker build -t tsd-api-mock:latest ../services/tsd-api-mock
  docker create \
    --name tsd \
    --hostname tsd \
    --network $LOCAL_NETWORK_NAME \
    -e CERT_PASSWORD=$SERVER_CERT_PASSWORD \
    -v $TMP_VOLUMES_DIR/tsd:/tsd/p11/data/durable/apps/ega/ \
    tsd-api-mock:latest \
    sh -c "sleep 5s && java -XX:+UseG1GC -jar app.jar"
  docker start tsd
  docker exec tsd mkdir -p /etc/ega/ssl/
  docker cp $TMP_CERTS_DIR/tsd/CA.cert tsd:/etc/ega/ssl/
  docker cp $TMP_CERTS_DIR/tsd/server.cert tsd:/etc/ega/ssl/
}

function build_and_run_db() {
  docker rm db
  docker create \
    --name db \
    --hostname db \
    --network $LOCAL_NETWORK_NAME \
    -e POSTGRES_DB=sda \
    -e PGDATA=/var/lib/postgresql/data \
    -e POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
    -e POSTGRES_SERVER_CERT=/etc/ega/pg.pem \
    -e POSTGRES_SERVER_KEY=/etc/ega/pg-server.pem \
    -e POSTGRES_SERVER_CACERT=/etc/ega/CA.pem \
    -e POSTGRES_VERIFY_PEER=verify-ca \
    -e DB_LEGA_IN_PASSWORD=$DB_LEGA_IN_PASSWORD \
    -e DB_LEGA_OUT_PASSWORD=$DB_LEGA_OUT_PASSWORD \
    -v $TMP_VOLUMES_DIR/db:/ega \
    --entrypoint /sda-pg-db-entrypoint.sh \
    $NEIC_SDA_POSTGRES_IMAGE \
    sh -c "sleep 5s && /usr/local/bin/entrypoint.sh postgres"
    docker start db
    docker exec db mkdir -p /etc/ega/
    docker cp $TMP_CERTS_DIR/db/pg.pem db:/etc/ega/
    docker cp $TMP_CERTS_DIR/db/pg-server.pem db:/etc/ega/
    docker cp $TMP_CERTS_DIR/db/CA.pem db:/etc/ega/
    docker cp $CONFS_DIR/sda-pg-db-entrypoint.sh db:/sda-pg-db-entrypoint.sh
}

function build_and_run_mq() {
  docker rm mq
  docker create \
    --name mq \
    --hostname mq \
    --network lega \
    --entrypoint /entrypoint.sh \
    -p 5671:5671 \
    rabbitmq:3.12.13-management-alpine \
    sh -c "rabbitmq-plugins enable --offline rabbitmq_federation rabbitmq_federation_management rabbitmq_shovel rabbitmq_shovel_management && rabbitmq-server"
  docker start mq
  docker cp $CONFS_DIR/mq/entrypoint.sh mq:/entrypoint.sh
  docker cp $TMP_CONFS_DIR/mq/. mq:/etc/rabbitmq/
  docker cp $TMP_CERTS_DIR/mq/. mq:/etc/rabbitmq/
}


function build_and_run_proxy() {
  docker rm proxy
  docker build -t tsd-proxy:latest ../services/localega-tsd-proxy
  docker create \
      --name proxy \
      --hostname proxy \
      --network $LOCAL_NETWORK_NAME \
      -e ROOT_CERT_PASSWORD=$ROOT_CERT_PASSWORD \
      -e TSD_ROOT_CERT_PASSWORD=$TSD_ROOT_CERT_PASSWORD \
      -e SERVER_CERT_PASSWORD=$SERVER_CERT_PASSWORD \
      -e CLIENT_ID=test \
      -e CLIENT_SECRET=test \
      -e BROKER_HOST=$BROKER_HOST \
      -e BROKER_PORT=$BROKER_PORT \
      -e BROKER_USERNAME=$BROKER_USERNAME \
      -e BROKER_PASSWORD=$BROKER_PASSWORD \
      -e BROKER_VHOST=$BROKER_VHOST \
      -e BROKER_VALIDATE=$BROKER_VALIDATE \
      -e EXCHANGE=$EXCHANGE \
      -e CEGA_AUTH_URL=$CEGA_AUTH_URL \
      -e CEGA_USERNAME=$CEGA_USERNAME \
      -e CEGA_PASSWORD=$CEGA_PASSWORD \
      -e TSD_HOST=$TSD_HOST \
      -e TSD_ACCESS_KEY=$TSD_ACCESS_KEY \
      -e POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
      -p 10443:8080 \
      tsd-proxy:latest
  docker cp $TMP_CERTS_DIR/proxy/CA.cert proxy:/etc/ega/ssl/
  docker cp $TMP_CERTS_DIR/proxy/server.cert proxy:/etc/ega/ssl/
  docker cp $TMP_CERTS_DIR/proxy/passport.pem proxy:/etc/ega/jwt/
  docker cp $TMP_CERTS_DIR/proxy/visa.pem proxy:/etc/ega/jwt/
  docker start proxy
}

function build_and_run_interceptor() {
  docker rm interceptor
  docker build -t mq-interceptor:latest ../services/mq-interceptor
  docker run -d --name interceptor \
    --hostname interceptor \
    --network lega \
    -e POSTGRES_CONNECTION=$POSTGRES_CONNECTION \
    -e CEGA_MQ_CONNECTION=$CEGA_MQ_CONNECTION \
    -e CEGA_MQ_EXCHANGE=localega \
    -e CEGA_MQ_QUEUE=v1.files \
    -e LEGA_MQ_CONNECTION=$MQ_CONNECTION \
    -e LEGA_MQ_EXCHANGE=sda \
    mq-interceptor:latest
}

function build_and_run_postgres() {
  docker rm postgres
  docker run -d \
    --name postgres \
    --hostname postgres \
    --network $LOCAL_NETWORK_NAME \
    -e POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
    -v $CONFS_DIR/postgres/init-mappings-db.sh:/docker-entrypoint-initdb.d/init-mappings-db.sh \
    -v $CONFS_DIR/postgres/entrypoint.sh:/entrypoint.sh \
    --entrypoint /entrypoint.sh \
    -p 5433:5432 \
    postgres
}


function build_and_run_sda_ingest() {
   docker rm ingest
   docker create \
     --name ingest \
     --hostname ingest \
     --network $LOCAL_NETWORK_NAME \
     -e ARCHIVE_TYPE=posix \
     -e ARCHIVE_LOCATION=/ega/archive \
     -e BROKER_HOST=$MQ_HOST \
     -e BROKER_PORT=5671 \
     -e BROKER_USER=$PRIVATE_BROKER_USER \
     -e BROKER_PASSWORD=$PRIVATE_BROKER_PASSWORD \
     -e BROKER_VHOST=$PRIVATE_BROKER_VHOST \
     -e BROKER_QUEUE=ingest \
     -e BROKER_EXCHANGE=sda \
     -e BROKER_ROUTINGKEY=archived \
     -e BROKER_ROUTINGERROR=error \
     -e BROKER_SSL=true \
     -e BROKER_VERIFYPEER=true \
     -e BROKER_CACERT=/etc/ega/CA.cert \
     -e BROKER_CLIENTCERT=/etc/ega/client.cert \
     -e BROKER_CLIENTKEY=/etc/ega/client-key.cert \
     -e C4GH_PASSPHRASE=$KEY_PASSWORD \
     -e C4GH_FILEPATH=/etc/ega/ega.sec \
     -e DB_HOST=$DB_HOST \
     -e DB_PORT=5432 \
     -e DB_USER=$DB_LEGA_IN_USER \
     -e DB_PASSWORD=$DB_LEGA_IN_PASSWORD \
     -e DB_DATABASE=lega \
     -e DB_SSLMODE=require \
     -e DB_CLIENTCERT=/etc/ega/client.cert \
     -e DB_CLIENTKEY=/etc/ega/client-key.cert \
     -e INBOX_TYPE=posix \
     -e INBOX_LOCATION=/ega/inbox \
     -e LOG_LEVEL=debug \
     -v $TMP_VOLUMES_DIR/tsd:/ega/inbox \
     -v $TMP_VOLUMES_DIR/vault:/ega/archive \
     $NEIC_SDA_STACK_IMAGE \
     "sda-ingest"
    docker cp $TMP_CERTS_DIR/sda/CA.cert ingest:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/client.cert ingest:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/client-key.cert ingest:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/ega.sec ingest:/etc/ega/
    docker start ingest
}

function build_and_run_sda_verify() {
   docker rm verify
   docker create \
     --name verify \
     --hostname verify \
     --network $LOCAL_NETWORK_NAME \
     -e ARCHIVE_TYPE=posix \
     -e ARCHIVE_LOCATION=/ega/archive \
     -e BROKER_HOST=$MQ_HOST \
     -e BROKER_PORT=5671 \
     -e BROKER_USER=$PRIVATE_BROKER_USER \
     -e BROKER_PASSWORD=$PRIVATE_BROKER_PASSWORD \
     -e BROKER_VHOST=$PRIVATE_BROKER_VHOST \
     -e BROKER_QUEUE=archived \
     -e BROKER_EXCHANGE=sda \
     -e BROKER_ROUTINGKEY=verified \
     -e BROKER_ROUTINGERROR=error \
     -e BROKER_SSL=true \
     -e BROKER_VERIFYPEER=true \
     -e BROKER_CACERT=/etc/ega/CA.cert \
     -e BROKER_CLIENTCERT=/etc/ega/client.cert \
     -e BROKER_CLIENTKEY=/etc/ega/client-key.cert \
     -e C4GH_PASSPHRASE=$KEY_PASSWORD \
     -e C4GH_FILEPATH=/etc/ega/ega.sec \
     -e DB_HOST=$DB_HOST \
     -e DB_PORT=5432 \
     -e DB_USER=$DB_LEGA_IN_USER \
     -e DB_PASSWORD=$DB_LEGA_IN_PASSWORD \
     -e DB_DATABASE=lega \
     -e DB_SSLMODE=require \
     -e DB_CLIENTCERT=/etc/ega/client.cert \
     -e DB_CLIENTKEY=/etc/ega/client-key.cert \
     -e INBOX_LOCATION=/ega/inbox \
     -e LOG_LEVEL=debug \
     -v $TMP_VOLUMES_DIR/vault:/ega/archive \
     -v $TMP_VOLUMES_DIR/tsd:/ega/inbox \
     $NEIC_SDA_STACK_IMAGE \
     "sda-verify"
    docker cp $TMP_CERTS_DIR/sda/CA.cert verify:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/client.cert verify:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/client-key.cert verify:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/ega.sec verify:/etc/ega/
    docker start verify
}

function build_and_run_sda_finalize() {
   docker rm finalize
   docker create \
     --name finalize \
     --hostname finalize \
     --network $LOCAL_NETWORK_NAME \
     -e BROKER_HOST=$MQ_HOST \
     -e BROKER_PORT=5671 \
     -e BROKER_USER=$PRIVATE_BROKER_USER \
     -e BROKER_PASSWORD=$PRIVATE_BROKER_PASSWORD \
     -e BROKER_VHOST=$PRIVATE_BROKER_VHOST \
     -e BROKER_QUEUE=accessionIDs \
     -e BROKER_EXCHANGE=sda \
     -e BROKER_ROUTINGKEY=completed \
     -e BROKER_ROUTINGERROR=error \
     -e BROKER_SSL=true \
     -e BROKER_VERIFYPEER=true \
     -e BROKER_CACERT=/etc/ega/CA.cert \
     -e BROKER_CLIENTCERT=/etc/ega/client.cert \
     -e BROKER_CLIENTKEY=/etc/ega/client-key.cert \
     -e DB_HOST=$DB_HOST \
     -e DB_PORT=5432 \
     -e DB_USER=$DB_LEGA_IN_USER \
     -e DB_PASSWORD=$DB_LEGA_IN_PASSWORD \
     -e DB_DATABASE=lega \
     -e DB_SSLMODE=require \
     -e DB_CLIENTCERT=/etc/ega/client.cert \
     -e DB_CLIENTKEY=/etc/ega/client-key.cert \
     -e LOG_LEVEL=debug \
     $NEIC_SDA_STACK_IMAGE \
     "sda-finalize"
    docker cp $TMP_CERTS_DIR/sda/CA.cert finalize:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/client.cert finalize:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/client-key.cert finalize:/etc/ega/
    docker start finalize
}

function build_and_run_sda_mapper() {
   docker rm mapper
   docker create \
     --name mapper \
     --hostname mapper \
     --network $LOCAL_NETWORK_NAME \
     -e BROKER_HOST=$MQ_HOST \
     -e BROKER_PORT=5671 \
     -e BROKER_USER=$PRIVATE_BROKER_USER \
     -e BROKER_PASSWORD=$PRIVATE_BROKER_PASSWORD \
     -e BROKER_VHOST=$PRIVATE_BROKER_VHOST \
     -e BROKER_QUEUE=mappings \
     -e BROKER_EXCHANGE=sda \
     -e BROKER_ROUTINGERROR=error \
     -e BROKER_SSL=true \
     -e BROKER_VERIFYPEER=true \
     -e BROKER_CACERT=/etc/ega/CA.cert \
     -e BROKER_CLIENTCERT=/etc/ega/client.cert \
     -e BROKER_CLIENTKEY=/etc/ega/client-key.cert \
     -e DB_HOST=$DB_HOST \
     -e DB_PORT=5432 \
     -e DB_USER=$DB_LEGA_OUT_USER \
     -e DB_PASSWORD=$DB_LEGA_OUT_PASSWORD \
     -e DB_DATABASE=lega \
     -e DB_SSLMODE=require \
     -e DB_CLIENTCERT=/etc/ega/client.cert \
     -e DB_CLIENTKEY=/etc/ega/client-key.cert \
     -e LOG_LEVEL=debug \
     $NEIC_SDA_STACK_IMAGE \
     "sda-mapper"
    docker cp $TMP_CERTS_DIR/sda/CA.cert mapper:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/client.cert mapper:/etc/ega/
    docker cp $TMP_CERTS_DIR/sda/client-key.cert mapper:/etc/ega/
    docker start mapper
}


function build_and_run_doa() {
    docker rm doa
    docker create \
       --name doa \
       --hostname doa \
       --network $LOCAL_NETWORK_NAME \
       -e SSL_MODE=require \
       -e SSL_ENABLED=false \
       -e ARCHIVE_PATH=$ARCHIVE_PATH \
       -e DB_INSTANCE=$DB_HOST \
       -e POSTGRES_DB=$DB_DATABASE_NAME \
       -e POSTGRES_PASSWORD=$DB_LEGA_OUT_PASSWORD \
       -e OUTBOX_ENABLED=false \
       -p "80:8080" \
       -v $TMP_VOLUMES_DIR/vault:/ega/archive \
       neicnordic/sda-doa:release-v1.6.0
    docker cp $TMP_CERTS_DIR/doa/CA.cert doa:/etc/ega/ssl/
    docker cp $TMP_CERTS_DIR/doa/client.cert doa:/etc/ega/ssl/
    docker cp $TMP_CERTS_DIR/doa/client.key doa:/etc/ega/ssl/
    docker cp $TMP_CERTS_DIR/doa/passport.pem doa:/etc/ega/jwt/
    docker cp $TMP_CERTS_DIR/doa/visa.pem doa:/etc/ega/jwt/
    docker cp $TMP_CERTS_DIR/doa/key.pem doa:/etc/ega/crypt4gh/
    docker cp $TMP_CERTS_DIR/doa/key.pass doa:/etc/ega/crypt4gh/
    docker start doa
}


function build_and_run_cegamq() {
  docker rm cegamq
  docker create \
     --name cegamq \
     --hostname cegamq \
     --network $LOCAL_NETWORK_NAME \
     -e RABBITMQ_CONFIG_FILE=/etc/rabbitmq/conf/cega \
     -e RABBITMQ_ENABLED_PLUGINS_FILE=/etc/rabbitmq/conf/cega.plugins \
     -p "5672:5671" \
     -p "15672:15672" \
     -p "25672:15672" \
     rabbitmq:3.12.13-management-alpine \
     sh -c "sleep 10s && chmod -R 0644 /etc/rabbitmq/conf/* && chown -R 100:101 /etc/rabbitmq/conf/* && rabbitmq-server"
  docker start cegamq
  docker exec cegamq mkdir -p /etc/rabbitmq/ssl/ /etc/rabbitmq/conf/
  docker cp $TMP_CERTS_DIR/cegamq/mq.pem cegamq:/etc/rabbitmq/ssl/
  docker cp $TMP_CERTS_DIR/cegamq/mq-key.pem cegamq:/etc/rabbitmq/ssl/
  docker cp $TMP_CERTS_DIR/cegamq/ca.pem cegamq:/etc/rabbitmq/ssl/
  docker cp $CONFS_DIR/cegamq/confs/. cegamq:/etc/rabbitmq/conf/
}

function build_and_run_cegaauth() {
  docker rm cegaauth
  docker create \
     --name cegaauth \
     --hostname cegaauth \
     --network $LOCAL_NETWORK_NAME \
     -e LEGA_INSTANCES=dummy \
     -e CEGA_USERS_PASSWORD=dummy \
     -e CEGA_USERS_USER=dummy \
     -p "8443:8443" \
     egarchive/lega-base:release.v0.2.0 \
     python /cega/cega-mock.py 0.0.0.0 8443 /cega/users.json
  docker cp $CONFS_DIR/cegamq/users/. cegaauth:/cega/
  docker start cegaauth
}

# Generates the required certificates for
# the containers deployed in docker.
function generate_certs() {

  # Step 0: Navigate to the temporary directory.
  # This is where we'll store all the certificates
  # in the host machine.
  cd $TMP_CERTS_DIR || exit 1

  mkcert="$LOCAL_BIN/mkcert"
  crypt4gh="$LOCAL_BIN/crypt4gh"

  # Step 1: Generate and install the root
  # certificate authority (CA) using mkcert
  $mkcert -install
  echo "CAROOT is $($mkcert -CAROOT)"

  # Step 2: Generate SSL/TLS certificates for
  # localhost and other services
  $mkcert localhost db vault mq tsd proxy

  # Step 3: Generate the client certificates for
  # localhost and other services
  $mkcert -client localhost db vault mq tsd proxy

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
  $crypt4gh generate -n ega -p ${KEY_PASSWORD}

  # Step 8,9: Copy root CA certificate and private key
  cp "$($mkcert -CAROOT)/rootCA.pem" rootCA.pem
  cp "$($mkcert -CAROOT)/rootCA-key.pem" rootCA-key.pem
  chmod 600 rootCA-key.pem

  # Step 10: Export root CA certificate to PKCS#12 format
  openssl pkcs12 -export \
    -out rootCA.p12 \
    -in rootCA.pem \
    -inkey rootCA-key.pem \
    -passout pass:${ROOT_CERT_PASSWORD}

  # Step 11: Copy server and client certificates
  cp localhost+5.pem server.pem
  cp localhost+5-key.pem server-key.pem
  cp localhost+5.p12 server.p12
  cp localhost+5-client.pem client.pem
  cp localhost+5-client-key.pem client-key.pem
  cp localhost+5-client-key.der client-key.der
  cp localhost+5-client.p12 client.p12

  # tsd
  mkdir -p tsd &&
    cp rootCA.pem tsd/rootCA.pem &&
    cp server.p12 tsd/server.p12

  # db
  mkdir -p db &&
    cp server.pem db/server.pem &&
    cp rootCA.pem db/rootCA.pem

  # mq
  mkdir -p mq &&
    cp server.pem mq/server.pem &&
    cp server-key.pem mq/server-key.pem &&
    cp rootCA.pem mq/rootCA.pem

  # proxy
  mkdir -p proxy &&
    cp rootCA.p12 proxy/rootCA.p12 &&
    cp server.p12 proxy/server.p12 &&
    cp jwt.pub.pem proxy/jwt.pub.pem

  # ingest,verify,finalize,mapper
  mkdir -p sda &&
    cp rootCA.pem sda/rootCA.pem &&
    cp client.pem sda/client.pem &&
    cp client-key.pem sda/client-key.pem &&
    cp ega.sec.pem sda/ega.sec.pem

  chmod -R 644 sda/*

  # doa
  mkdir -p doa &&
    cp rootCA.pem doa/rootCA.pem &&
    cp client.pem doa/client.pem &&
    cp client-key.der doa/client-key.der &&
    cp jwt.pub.pem doa/jwt.pub.pem &&
    cp ega.sec.pem doa/ega.sec.pem &&
    cp ega.sec.pass doa/ega.sec.pass

  # cegamq
  mkdir -p cegamq &&
    cp server.pem cegamq/mq.pem &&
    cp server-key.pem cegamq/mq-key.pem &&
    cp rootCA.pem cegamq/ca.pem

  cd ../../

}

create_lega_network_if_not_exists

if [ $# -ge 1 ]; then
  case "$1" in
  "build_and_run_cegaauth")
    build_and_run_cegaauth
    ;;
  "build_and_run_cegamq")
    build_and_run_cegamq
    ;;
  "build_and_run_tsd")
    build_and_run_tsd
    ;;
  "build_and_run_db")
    build_and_run_db
    ;;
  "build_and_run_mq")
    build_and_run_mq
    ;;
  "build_and_run_postgres")
    build_and_run_postgres
    ;;
  "build_and_run_proxy")
    build_and_run_proxy
    ;;
  "build_and_run_interceptor")
    build_and_run_interceptor
    ;;
  "build_and_run_sda_ingest")
    build_and_run_sda_ingest
    ;;
  "build_and_run_sda_verify")
    build_and_run_sda_verify
    ;;
    "build_and_run_sda_finalize")
      build_and_run_sda_finalize
      ;;
      "build_and_run_sda_mapper")
        build_and_run_sda_mapper
        ;;
  "build_and_run_doa")
          build_and_run_doa
          ;;
  *)
    echo "Invalid action. Usage: $0"
    exit 1
    ;;
  esac
fi