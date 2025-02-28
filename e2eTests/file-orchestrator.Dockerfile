# Use a lightweight base image with necessary tools
FROM --platform=linux/amd64 eclipse-temurin:21-jdk-alpine

ARG MKCERT_VERSION="v1.4.4"
ARG LOCAL_BIN="/usr/local/bin"

WORKDIR /storage

RUN apk update && apk add --no-cache bash

RUN mkdir -p "confs"
RUN mkdir -p "certs"

COPY confs confs
COPY tmp/* certs/
COPY scripts/* .

COPY .env .env

RUN chmod +x *.sh

ENTRYPOINT [ "./entrypoint.sh" ]

# Add a HEALTHCHECK to verify readiness
HEALTHCHECK --interval=5s --timeout=3s --retries=5 CMD [ "/bin/sh", "-c", "[ -f /storage/ready ] || exit 1" ]
