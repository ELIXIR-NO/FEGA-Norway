#!/bin/sh

set -e

echo "Running pre-initialization script as $(whoami)"

chmod -R 600 /docker-entrypoint-initdb.d/*
find /docker-entrypoint-initdb.d/ -type f -name "*.sh" -exec chmod 700 {} \;
chown -R postgres:postgres /docker-entrypoint-initdb.d/*

ls -alh /docker-entrypoint-initdb.d

/usr/local/bin/docker-entrypoint.sh postgres

