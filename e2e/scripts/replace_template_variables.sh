#!/bin/bash

source env.sh

function escape_special_chars() {
  echo "$1" | sed -e 's/[]\/$*.^[]/\\&/g'
}

function frepl() {
  local search=$(escape_special_chars "$1")
  local replace=$(escape_special_chars "$2")
  sed -i.bak "s/$search/$replace/g" "$3"
  rm -rf "$3.bak"
}

 # mq
definitions_json_file=/volumes/mq-confs-and-certs/definitions.json
frepl "<<MQ_USER>>" "$MQ_USER" $definitions_json_file
frepl "<<MQ_PASSWORD_HASH>>" "$MQ_PASSWORD_HASH" $definitions_json_file
frepl "<<MQ_VHOST>>" "$MQ_VHOST" $definitions_json_file
rabbitmq_conf_file=/volumes/mq-confs-and-certs/rabbitmq.conf
frepl "<<MQ_SSL_VERIFY>>" "$MQ_SSL_VERIFY" $rabbitmq_conf_file
frepl "<<MQ_SSL_FAIL_IF_NO_PEER_CERT>>" "$MQ_SSL_FAIL_IF_NO_PEER_CERT" $rabbitmq_conf_file
frepl "<<MQ_SSL_DEPTH>>" "$MQ_SSL_DEPTH" $rabbitmq_conf_file

 # sda pipeline
sda_config_file=/volumes/sda-config/config.yaml
frepl "<<SDA_C4GH_FILEPATH>>" "$SDA_C4GH_FILEPATH" $sda_config_file
frepl "<<SDA_C4GH_PASSPHRASE>>" "$SDA_C4GH_PASSPHRASE" $sda_config_file
frepl "<<SDA_INBOX_LOCATION>>" "$SDA_INBOX_LOCATION" $sda_config_file
frepl "<<SDA_ARCHIVE_LOCATION>>" "$SDA_ARCHIVE_LOCATION" $sda_config_file

echo "Replaced all the template variables using .env ✅"
