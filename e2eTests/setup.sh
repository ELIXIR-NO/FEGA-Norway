#!/bin/bash
export WORKING_DIR="./tmp"
export TMP_CERT_DIR="$WORKING_DIR/certs"
export DEV_SCRIPTS_DIR="./dev" # development
export LOCAL_VOLUME_MAPPING_DIR="$WORKING_DIR"

export CAROOT="$(mkcert -CAROOT)"
export TSD_API_MOCK_CERT_PASSWORD=server_cert_passw0rd
export CLIENT_CERT_PASSWORD=client_cert_passw0rd
export ROOT_CERT_PASSWORD=r00t_cert_passw0rd
export KEY_PASSWORD=key_passw0rd # Also used by SDA

export CEGA_AUTH_URL=http://cega-auth:8443/lega/v1/legas/users/
export CEGA_USERNAME=dummy
export CEGA_PASSWORD=dummy
export CEGA_MQ_CONNECTION=amqps://test:test@cegamq:5671/lega?cacertfile=/etc/ega/ssl/CA.cert

export EGA_BOX_USERNAME=dummy
export EGA_BOX_PASSWORD=dummy

export BROKER_HOST=cegamq
export BROKER_PORT=5671
export BROKER_USERNAME=test
export BROKER_PASSWORD=test
export BROKER_VHOST=lega
export BROKER_VALIDATE=false

export EXCHANGE=localega.v1

export TSD_ROOT_CERT_PASSWORD=r00t_cert_passw0rd
export TSD_HOST=tsd:8080
export TSD_PROJECT=p11
export TSD_ACCESS_KEY=s0me_key

export DB_HOST=db
export DB_DATABASE_NAME=lega

export DB_LEGA_IN_USER=lega_in
export DB_LEGA_IN_PASSWORD=in_passw0rd
export DB_LEGA_OUT_USER=lega_out
export DB_LEGA_OUT_PASSWORD=0ut_passw0rd

export PRIVATE_BROKER_VHOST=test # Also used by SDA
export PRIVATE_BROKER_USER=admin # Also used by SDA
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

export FILES=("localhost+5.pem" "localhost+5-key.pem" "localhost+5-client.pem" "localhost+5-client-key.pem" "rootCA.pem" "rootCA.p12" "localhost+5.p12" "localhost+5-client.p12" "localhost+5-client-key.der" "rootCA-key.pem" "docker-stack.yml" "jwt.pub.pem" "jwt.priv.pem" "ega.pub.pem" "ega.sec.pass" "ega.sec.pem" "server.pem" "server-key.pem" "server.p12" "client.pem" "client-key.pem" "client-key.der" "client.p12")

function apply_configs() {

  # Check if the source template file exists
  if [ -f "docker-compose.template.yml" ]; then
      # Copy the content of docker-compose.template.yml to docker-compose.yml
      cp docker-compose.template.yml ./docker-compose.yml
      rm -rf docker-compose.yml.bak > /dev/null 2>&1
      echo "docker-compose.yml has been successfully created from the template."
  else
      echo "Error: docker-compose.template.yml does not exist."
  fi

  local f=docker-compose.yml

  # tsd-api-mock
  frepl "<<TSD_API_MOCK_CERT_PASSWORD>>" "$TSD_API_MOCK_CERT_PASSWORD" $f
  frepl "<<DEV_CERTS_DIR>>" "$TMP_CERT_DIR" $f
  frepl "<<DEV_SCRIPTS_DIR>>" "$DEV_SCRIPTS_DIR" $f
  frepl "<<LOCAL_VOLUME_MAPPING_DIR>>" "$WORKING_DIR" $f

  # sda-db
  frepl "<<SDA_DB_LEGA_IN_PASSWORD>>" "$DB_LEGA_IN_PASSWORD" $f
  frepl "<<SDA_DB_LEGA_OUT_PASSWORD>>" "$DB_LEGA_OUT_PASSWORD" $f
  frepl "<<SDA_DB_POSTGRES_PASSWORD>>" "passw0rd" $f # FIXME

  # rabbitmq
  frepl "<<LOCAL_EGA_BROKER_VIRTUAL_HOST>>" "$PRIVATE_BROKER_VHOST" $f
  frepl "<<LOCAL_EGA_BROKER_USER_NAME>>" "$PRIVATE_BROKER_USER" $f
  frepl "<<LOCAL_EGA_BROKER_PASSWORD_HASH>>" "$PRIVATE_BROKER_HASH" $f

  # proxy
  frepl "<<PROXY_ROOT_CERT_PASSWORD>>" "$ROOT_CERT_PASSWORD" $f
  frepl "<<PROXY_TSD_ROOT_CERT_PASSWORD>>" "$TSD_ROOT_CERT_PASSWORD" $f
  frepl "<<PROXY_SERVER_CERT_PASSWORD>>" "$SERVER_CERT_PASSWORD" $f
  frepl "<<PROXY_CLIENT_ID>>" "test" $f
  frepl "<<PROXY_CLIENT_SECRET>>" "test" $f
  frepl "<<PROXY_BROKER_HOST>>" "$BROKER_HOST" $f
  frepl "<<PROXY_BROKER_PORT>>" "$BROKER_PORT" $f
  frepl "<<PROXY_BROKER_USERNAME>>" "$BROKER_USERNAME" $f
  frepl "<<PROXY_BROKER_PASSWORD>>" "$BROKER_PASSWORD" $f
  frepl "<<PROXY_BROKER_VHOST>>" "$BROKER_VHOST" $f
  frepl "<<PROXY_BROKER_VALIDATE>>" "$BROKER_VALIDATE" $f
  frepl "<<PROXY_EXCHANGE>>" "$EXCHANGE" $f
  frepl "<<PROXY_CEGA_AUTH_URL>>" "$CEGA_AUTH_URL" $f
  frepl "<<PROXY_CEGA_USERNAME>>" "$CEGA_USERNAME" $f
  frepl "<<PROXY_CEGA_PASSWORD>>" "$CEGA_PASSWORD" $f
  frepl "<<PROXY_TSD_HOST>>" "$TSD_HOST" $f
  frepl "<<PROXY_TSD_ACCESS_KEY>>" "$TSD_ACCESS_KEY" $f
  frepl "<<PROXY_POSTGRES_PASSWORD>>" "$POSTGRES_PASSWORD" $f

  # interceptor
  frepl "<<INTERCEPTOR_POSTGRES_CONNECTION>>" "$POSTGRES_CONNECTION" $f
  frepl "<<INTERCEPTOR_CEGA_MQ_CONNECTION>>" "$CEGA_MQ_CONNECTION" $f
  frepl "<<INTERCEPTOR_MQ_CONNECTION>>" "$MQ_CONNECTION" $f

  # ingest, verify, finalize, mapper
  frepl "<<BROKER_HOST>>" "$MQ_HOST" $f
  frepl "<<PRIVATE_BROKER_USER>>" "$PRIVATE_BROKER_USER" $f
  frepl "<<PRIVATE_BROKER_PASSWORD>>" "$PRIVATE_BROKER_PASSWORD" $f
  frepl "<<PRIVATE_BROKER_VHOST>>" "$PRIVATE_BROKER_VHOST" $f
  frepl "<<C4GH_PASSPHRASE>>" "$KEY_PASSWORD" $f
  frepl "<<DB_HOST>>" "$DB_HOST" $f
  frepl "<<DB_LEGA_IN_USER>>" "$DB_LEGA_IN_USER" $f
  frepl "<<DB_LEGA_IN_PASSWORD>>" "$DB_LEGA_IN_PASSWORD" $f
  frepl "<<MQ_HOST>>" "$MQ_HOST" $f
  frepl "<<DB_LEGA_OUT_USER>>" "$DB_LEGA_OUT_USER" $f
  frepl "<<DB_LEGA_OUT_PASSWORD>>" "$DB_LEGA_OUT_PASSWORD" $f

  # doa
  frepl "<<ARCHIVE_PATH>>" "$ARCHIVE_PATH" $f
  frepl "<<DB_HOST>>" "$DB_HOST" $f
  frepl "<<DB_DATABASE_NAME>>" "$DB_DATABASE_NAME" $f
  frepl "<<DB_LEGA_OUT_PASSWORD>>" "$DB_LEGA_OUT_PASSWORD" $f

}

# Generates the required certificates for
# the services deployed in the swarm.
function generate_certs() {

  # Step 0: Navigate to the temporary directory.
  # This is where we'll store all the certificates
  # in the host machine.
  cd $TMP_CERT_DIR || exit 1

  # Step 1: Generate and install the root
  # certificate authority (CA) using mkcert
  mkcert -install
  echo "CAROOT is $CAROOT"

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
    -inkey localhost+5-key.pem -passout pass:"${TSD_API_MOCK_CERT_PASSWORD}"
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

  # Step 7: Create Docker secrets for JWT private
  # key, JWT public key, and other secrets
  docker secret create jwt.priv.pem jwt.priv.pem
  openssl rsa -pubout -in jwt.priv.pem -out jwt.pub.pem
  docker secret create jwt.pub.pem jwt.pub.pem
  printf "%s" "${KEY_PASSWORD}" >ega.sec.pass
  docker secret create ega.sec.pass ega.sec.pass
  crypt4gh generate -n ega -p ${KEY_PASSWORD}
  docker secret create ega.sec.pem ega.sec.pem

  # Step 8,9: Copy root CA certificate and private key
  cp "$CAROOT/rootCA.pem" rootCA.pem
  docker secret create rootCA.pem rootCA.pem
  cp "$CAROOT/rootCA-key.pem" rootCA-key.pem
  chmod 600 rootCA-key.pem
  docker secret create rootCA-key.pem rootCA-key.pem

  # Step 10: Export root CA certificate to PKCS#12 format
  openssl pkcs12 -export \
    -out rootCA.p12 \
    -in rootCA.pem \
    -inkey rootCA-key.pem \
    -passout pass:${ROOT_CERT_PASSWORD}
  docker secret create rootCA.p12 rootCA.p12

  # Step 11: Copy and create Docker secrets
  # for server and client certificates
  cp localhost+5.pem server.pem
  docker secret create server.pem server.pem
  cp localhost+5-key.pem server-key.pem
  docker secret create server-key.pem server-key.pem
  cp localhost+5.p12 server.p12
  docker secret create server.p12 server.p12
  cp localhost+5-client.pem client.pem
  docker secret create client.pem client.pem
  cp localhost+5-client-key.pem client-key.pem
  docker secret create client-key.pem client-key.pem
  cp localhost+5-client-key.der client-key.der
  docker secret create client-key.der client-key.der
  cp localhost+5-client.p12 client.p12
  docker secret create client.p12 client.p12

  cd ../

}

# Invokers --

function init() {
  if ! check_dependencies; then
    echo "Dependency check failed. Exiting."
    exit 1
  fi
  # Create and own the temporary dirs
  mkdir -p $TMP_CERT_DIR $WORKING_DIR/tsd $WORKING_DIR/vault $WORKING_DIR/db
  # chown 65534:65534 $WORKING_DIR/vault $WORKING_DIR/tsd
  chmod 777 $WORKING_DIR/tsd $WORKING_DIR/vault $WORKING_DIR/db
}

function clean() {
  rm -rf $WORKING_DIR
  rm -rf docker-compose.ym*
  echo "Cleanup completed 💯"
}

function start() {
  echo "Starting the LEGA stack 🚀"
  docker-compose up -d
}

function stop() {
  echo "Stopping the LEGA stack 🛑"
  docker-compose down
}

# Utility functions --

# Check the existence of a passed command but discard
# the outcome through redirection whether its successful
# or erroneous.
function exists() {
  command -v "$1" 1>/dev/null 2>&1
}

function escape_special_chars() {
    echo "$1" | sed -e 's/[]\/$*.^[]/\\&/g'
}

# Find and replace all the strings matching target
# in a specified file.
function frepl() {
    local search=$(escape_special_chars "$1")
    local replace=$(escape_special_chars "$2")
    sed -i.bak "s/$search/$replace/g" "$3"
}

# Pre-condition function to check
# for required dependencies
function check_dependencies() {
  local missing_deps=0
  # Define an array of dependencies
  local deps=("mkcert" "openssl" "docker" "crypt4gh")
  echo "Checking for required dependencies..."
  for dep in "${deps[@]}"; do
    if ! exists "$dep"; then
      echo "Error: '$dep' is not installed." >&2
      missing_deps=$((missing_deps + 1))
    fi
  done
  if [ $missing_deps -ne 0 ]; then
    echo "Please install the missing dependencies before proceeding." >&2
    return 1 # Return a non-zero status to indicate failure
  else
    echo "All required dependencies are installed."
  fi
}


# Entry --

if [ $# -ne 1 ]; then
  echo "Usage: $0 [init|generate_certs|start|stop|clean]"
  exit 1
fi

# Parse the action argument and perform
# the corresponding action
case "$1" in
"init")
  init
  ;;
"generate_certs")
  generate_certs
  ;;
"apply_configs")
  apply_configs
  ;;
"start")
  start
  ;;
"stop")
  stop
  ;;
"clean")
  clean
  ;;
*)
  echo "Invalid action. Usage: $0 [init|generate_certs|start|stop|clean]"
  exit 1
  ;;

esac
