#!/bin/sh

chmod -R 777 /volumes &&
  echo "Current context: $(pwd)" &&
  ls -alh &&
  ./generate_certs.sh &&
  ./copy_certificates_to_dest.sh &&
  ./copy_confs_to_dest.sh &&
  ./copy_json_schemas_to_dest.sh &&
  ./replace_template_variables.sh &&
  ./change_ownerships.sh &&
  # Signal readiness to the healthchecks that gate the other services.
  touch /storage/ready &&
  # Keep the container alive; nothing else would keep the process running.
  tail -f /dev/null
