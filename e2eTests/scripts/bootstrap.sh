#!/bin/bash

source .env

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
  rm -rf "$3.bak"
}

# Functionalities --

function apply_configs() {

  # Check if the source template file exists
  if [ -f "docker-compose.template.yml" ]; then
    # Copy the content of docker-compose.template.yml to docker-compose.yml
    cp docker-compose.template.yml ./docker-compose.yml
    rm -rf docker-compose.yml.bak >/dev/null 2>&1
    echo "docker-compose.yml has been successfully created from the template."
  else
    echo "Error: docker-compose.template.yml does not exist."
  fi

  local f=docker-compose.yml

  # tsd
  frepl "<<SERVER_CERT_PASSWORD>>" "$SERVER_CERT_PASSWORD" $f

  # db
  frepl "<<SDA_DB_USERNAME>>" "$SDA_DB_USERNAME" $f
  frepl "<<SDA_DB_PASSWORD>>" "$SDA_DB_PASSWORD" $f

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
  frepl "<<PROXY_BROKER_SSL_ENABLED>>" "$BROKER_SSL_ENABLED" $f
  frepl "<<PROXY_EXCHANGE>>" "$EXCHANGE" $f
  frepl "<<PROXY_CEGA_AUTH_URL>>" "$CEGA_AUTH_URL" $f
  frepl "<<PROXY_CEGA_USERNAME>>" "$CEGA_USERNAME" $f
  frepl "<<PROXY_CEGA_PASSWORD>>" "$CEGA_PASSWORD" $f
  frepl "<<PROXY_TSD_HOST>>" "$TSD_HOST" $f
  frepl "<<PROXY_TSD_ACCESS_KEY>>" "$TSD_ACCESS_KEY" $f
  frepl "<<PROXY_POSTGRES_PASSWORD>>" "$POSTGRES_PASSWORD" $f

  # interceptor
  frepl "<<INTERCEPTOR_POSTGRES_CONNECTION>>" "$POSTGRES_CONNECTION" $f
  frepl "<<INTERCEPTOR_MQ_CONNECTION>>" "$MQ_CONNECTION" $f
  frepl "<<CEGAMQ_USERNAME>>" "$BROKER_USERNAME" $f
  frepl "<<CEGAMQ_PASSWORD>>" "$BROKER_PASSWORD" $f
  frepl "<<CEGAMQ_HOST>>" "$BROKER_HOST" $f
  frepl "<<CEGAMQ_PORT>>" "$BROKER_PORT" $f
  frepl "<<CEGAMQ_VHOST>>" "$BROKER_VHOST" $f

  # postgres
  frepl "<<POSTGRES_PASSWORD>>" "$POSTGRES_PASSWORD" $f

  # ingest, verify, finalize, mapper, intercept, heartbeat
  frepl "<<BROKER_HOST>>" "$MQ_HOST" $f
  frepl "<<PRIVATE_BROKER_USER>>" "$PRIVATE_BROKER_USER" $f
  frepl "<<PRIVATE_BROKER_PASSWORD>>" "$PRIVATE_BROKER_PASSWORD" $f
  frepl "<<PRIVATE_BROKER_VHOST>>" "$PRIVATE_BROKER_VHOST" $f
  frepl "<<C4GH_PASSPHRASE>>" "$KEY_PASSWORD" $f
  frepl "<<SDA_DB_HOST>>" "$SDA_DB_HOST" $f
  frepl "<<SDA_DB_USERNAME>>" "$SDA_DB_USERNAME" $f
  frepl "<<SDA_DB_PASSWORD>>" "$SDA_DB_PASSWORD" $f
  frepl "<<SDA_DB_DATABASE_NAME>>" "$SDA_DB_DATABASE_NAME" $f
  frepl "<<MQ_HOST>>" "$MQ_HOST" $f
  frepl "<<MQ_PORT>>" "$MQ_PORT" $f

  # doa
  frepl "<<ARCHIVE_PATH>>" "$ARCHIVE_PATH" $f
  frepl "<<SDA_DB_HOST>>" "$SDA_DB_HOST" $f
  frepl "<<SDA_DB_DATABASE_NAME>>" "$SDA_DB_DATABASE_NAME" $f
  frepl "<<SDA_DB_PASSWORD>>" "$SDA_DB_PASSWORD" $f
  frepl "<<KEYSTORE_PASSWORD>>" "$SERVER_CERT_PASSWORD" $f

}

function check_requirements() {

    # Check if Docker is running
    if ! docker info &> /dev/null; then
        echo "Docker is not running"
        return 1
    fi

    # Check if the current user can execute Docker commands without sudo
    if ! docker ps &> /dev/null; then
        echo "The current user cannot execute Docker commands without sudo"
        return 1
    fi

    # Check if Docker is installed
    if ! command -v docker &> /dev/null; then
        echo "Docker is not installed"
        return 1
    fi

    # Check if Docker Compose is available
    if ! docker compose version &> /dev/null; then
        echo "Docker Compose is not available"
        return 1
    fi

    echo "All requirements satisfied"
    return 0

}

# Entrypoint --

usage="[check_requirements|apply_configs]"
if [ $# -ge 1 ]; then
  # Parse the action argument and perform
  # the corresponding action
  case "$1" in
  "check_requirements")
    check_requirements
    ;;
  "apply_configs")
    apply_configs
    ;;
  *)
    echo "Invalid action. Usage: $0 $usage"
    exit 1
    ;;
  esac
fi
