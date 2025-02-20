#!/bin/sh

pwd

ls -alh

# We recursively set chmod 777 on the volumes/ directory and its subdirectories.
chmod -R 777 /volumes
./copy_certificates_to_dest.sh
./copy_confs_to_dest.sh
./replace_template_variables.sh
./change_ownerships.sh
touch /storage/ready
tail -f /dev/null
