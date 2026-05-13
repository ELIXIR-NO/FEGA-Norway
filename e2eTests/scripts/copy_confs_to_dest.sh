#!/bin/bash

cd confs || exit 1

 # mq
cp -R ./mq/* /volumes/mq-confs-and-certs/

# heartbeat-pub
# We use a common volume for both pub/sub components.
cp -R ./heartbeat-pub/* /volumes/heartbeat-confs

# postgres
cp -R ./postgres/* /volumes/postgres-confs/

# cegamq
cp -R ./cegamq/* /volumes/cegamq-confs

# sda pipeline
cp -R ./sda/* /volumes/sda-config