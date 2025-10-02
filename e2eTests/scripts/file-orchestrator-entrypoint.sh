#!/bin/sh

# We recursively set chmod 777 on the
# volumes/ directory and its subdirectories.
chmod -R 777 /volumes &&
  echo "Current context: $(pwd)" &&
  ls -alh &&
  # Execute the scripts in this order.
  ./generate_certs.sh &&
  ./copy_certificates_to_dest.sh &&
  ./copy_confs_to_dest.sh &&
  ./replace_template_variables.sh &&
  ./change_ownerships.sh &&
  # Mark the container to be ready.
  touch /storage/ready &&
  # Run infinitely.
  tail -f /dev/null
