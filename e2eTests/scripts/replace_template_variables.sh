#!/bin/bash

source .env

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
frepl "<<USER_NAME>>" "$PRIVATE_BROKER_USER" $definitions_json_file
frepl "<<PASSWORD_HASH>>" "$PRIVATE_BROKER_HASH" $definitions_json_file
frepl "<<VIRTUAL_HOST>>" "$PRIVATE_BROKER_VHOST" $definitions_json_file

echo "Replaced all the template variables using .env ✅"
