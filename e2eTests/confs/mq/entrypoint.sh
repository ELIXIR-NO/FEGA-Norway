#!/bin/sh

set -e

echo "Running pre-initialization script as $(whoami)"

chmod -R 600 /etc/rabbitmq/*
chown -R rabbitmq:rabbitmq /etc/rabbitmq/*

exec "$@"

