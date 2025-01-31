#!/bin/bash

cd confs || exit 1

 # mq
cp -R ./mq/* /volumes/mq-confs-and-certs/
chmod -R 0640 /volumes/mq-confs-and-certs
chown -R 100:101 /volumes/mq-confs-and-certs
echo "Inspecting /volumes/mq-confs-and-certs/"
ls -alh /volumes/mq-confs-and-certs/
echo "\n\n"

# heartbeat-pub
cp -R ./heartbeat-pub/* /volumes/heartbeat-pub-confs
echo "Inspecting /volumes/heartbeat-pub-confs"
ls -alh /volumes/heartbeat-pub-confs
echo "\n\n"

# postgres
cp -R ./postgres/* /volumes/postgres-confs/
chown -R 999:999 /volumes/postgres-confs
find /volumes/postgres-confs/ -type f -name '*.sh' -exec chmod 700 {} \;
echo "Inspecting /volumes/postgres-confs/"
ls -alh /volumes/postgres-confs/
echo "\n\n"

# cegamq
cp -R ./cegamq/* /volumes/cegamq-confs
chmod -R 0640 /volumes/cegamq-confs/*
chown -R 100:101 /volumes/cegamq-confs/*
echo "Inspecting /volumes/cegamq-confs/"
ls -alh /volumes/cegamq-confs/
echo "\n\n"

# sda
chmod -R 0644 /volumes/sda-certs
chown -R nobody:nogroup /volumes/sda-certs/*
echo "Inspecting /volumes/sda-certs"
ls -alh /volumes/sda-certs
echo "\n\n"

# db
chmod -R 0640 /volumes/db-data /volumes/db-certs
echo "Inspecting /volumes/db-data AND /volumes/db-certs"
ls -alh /volumes/db-data
ls -alh /volumes/db-certs
echo "\n\n"

cd .. # Go back to the working directory.

echo "Done modifying the file ownerships to match expected uid:gid ✅"
