#!/bin/bash

cd confs || exit 1

 # mq
cp -R ./mq/* /volumes/mq-confs-and-certs/
chmod -R 600 /volumes/mq-confs-and-certs
chown -R 100:101 /volumes/mq-confs-and-certs

# heartbeat-pub
cp -R ./heartbeat-pub/* /volumes/heartbeat-pub-confs

# postgres
cp -R ./postgres/* /volumes/postgres-confs/
chown -R 999:999 /volumes/postgres-confs
find /volumes/postgres-confs/ -type f -name '*.sh' -exec chmod 700 {} \;

# cegamq
cp -R ./cegamq/* /volumes/cegamq-confs
chmod -R 0644 /volumes/cegamq-confs/*
chown -R 100:101 /volumes/cegamq-confs/*

cd .. # Go back to the working directory.

echo "Done modifying the file ownerships to match expected uid:gid ✅"
