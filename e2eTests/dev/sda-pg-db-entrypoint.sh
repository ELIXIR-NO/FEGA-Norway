#!/bin/sh

chmod -R 0600 /etc/ega/*
chown postgres:postgres /etc/ega/*

exec "$@"
