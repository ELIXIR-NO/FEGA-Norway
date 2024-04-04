#!/bin/bash

source setup.sh

docker rm --force $(docker ps -aq)

docker network rm lega
