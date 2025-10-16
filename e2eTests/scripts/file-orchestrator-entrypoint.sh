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
  ./change_ownerships.sh

# Wait until all key subdirectories in /storage are non-empty
for dir in certs confs; do
  echo "Checking $dir..."
  while [ -z "$(ls -A /storage/$dir 2>/dev/null)" ]; do
    echo "Waiting for /storage/$dir to have content..."
    sleep 2
  done
done

# Mark the container ready
touch /storage/ready
echo "Container is ready."

# Keep container alive
tail -f /dev/null
