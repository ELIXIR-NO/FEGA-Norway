#!/bin/bash

source .env

cd confs || exit 1

# proxy
chmod -R 777 /volumes/proxy-certs/*
chmod 777 /volumes/proxy-certs/jwt /volumes/proxy-certs/ssl /volumes/proxy-certs/store
echo "Inspecting /volumes/proxy-certs"
ls -alh /volumes/proxy-certs
echo

 # mq
chmod -R 0640 /volumes/mq-confs-and-certs
chown -R 100:101 /volumes/mq-confs-and-certs
echo "Inspecting /volumes/mq-confs-and-certs/"
ls -alh /volumes/mq-confs-and-certs/
echo

# heartbeat-pub
echo "Inspecting /volumes/heartbeat-pub-confs"
ls -alh /volumes/heartbeat-pub-confs
echo

# postgres
chown -R 999:999 /volumes/postgres-confs
chmod 755 /volumes/postgres-confs
find /volumes/postgres-confs/ -type f -name '*.sh' -exec chmod 700 {} \;
echo "Inspecting /volumes/postgres-confs/"
ls -alh /volumes/postgres-confs/
echo

# cegamq
chmod -R 0640 /volumes/cegamq-certs/* /volumes/cegamq-confs/*
chown -R 100:101 /volumes/cegamq-certs/* /volumes/cegamq-confs/*
chmod 755 /volumes/cegamq-certs /volumes/cegamq-confs
echo "Inspecting /volumes/cegamq-certs & /volumes/cegamq-confs/"
ls -alh /volumes/cegamq-confs/
ls -alh /volumes/cegamq-certs/
echo

# sda
chmod -R 0644 /volumes/sda-certs/
chmod -R 0600 /volumes/db-client-certs/
chown -R 65534:65534 /volumes/sda-certs/ /volumes/db-client-certs/
chmod 755 /volumes/sda-certs
chmod 755 /volumes/db-client-certs
echo "Inspecting /volumes/sda-certs"
ls -alh /volumes/sda-certs
ls -alh /volumes/db-client-certs/
echo

# db
chmod -R 600 /volumes/db-data /volumes/db-certs
chown -R 70:70 /volumes/db-data /volumes/db-certs
chmod 755 /volumes/db-certs
echo "Inspecting /volumes/db-data & /volumes/db-certs"
ls -alh /volumes/db-data
ls -alh /volumes/db-certs
echo

# doa
chmod -R 777 /volumes/doa-certs/
chown -R 65534:65534 /volumes/doa-certs/
chmod 755 /volumes/doa-certs/
echo "Inspecting /volumes/db-data & /volumes/db-certs"
ls -alh /volumes/doa-certs/
echo

cd .. # Go back to the working directory.

echo "Done modifying the file ownerships to match expected uid:gid(s) ✅"
