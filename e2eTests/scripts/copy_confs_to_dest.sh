#!/bin/bash

source .env

cd confs || exit 1

 # mq
cp -R ./mq/* /volumes/mq-confs-and-certs/

# heartbeat-pub
cp -R ./heartbeat-pub/* /volumes/heartbeat-pub-confs

# postgres
cp -R ./postgres/* /volumes/postgres-confs/

# cegamq
cp -R ./cegamq/* /volumes/cegamq-confs
