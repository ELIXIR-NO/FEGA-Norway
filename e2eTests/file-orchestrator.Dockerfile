# Use a lightweight base image with necessary tools
FROM --platform=linux/amd64 eclipse-temurin:21-jdk-alpine

ARG MKCERT_VERSION="v1.4.4"
ARG LOCAL_BIN="/usr/local/bin"

WORKDIR /storage

RUN apk update && apk add --no-cache bash openssl curl

# Install mkcert
RUN echo "Installing mkcert locally..." && \
    curl -fsSL "https://github.com/FiloSottile/mkcert/releases/download/${MKCERT_VERSION}/mkcert-${MKCERT_VERSION}-linux-amd64" -o "${LOCAL_BIN}/mkcert" && \
    chmod +x "${LOCAL_BIN}/mkcert" && \
    echo "mkcert installed successfully for the current user."

# Install crypt4gh
RUN echo "Installing crypt4gh locally..." && \
    curl -fsSL "https://raw.githubusercontent.com/neicnordic/crypt4gh/master/install.sh" | sh -s -- -b "$LOCAL_BIN" && \
    chmod +x "$LOCAL_BIN/crypt4gh" && \
    echo "crypt4gh installed successfully for the current user."

RUN mkdir -p "confs"
RUN mkdir -p "certs"

COPY confs confs
COPY scripts/* .

COPY .env .env

RUN chmod +x *.sh

ENTRYPOINT [ "./entrypoint.sh" ]

# Add a HEALTHCHECK to verify readiness
HEALTHCHECK --interval=5s --timeout=3s --retries=5 CMD [ "/bin/sh", "-c", "[ -f /storage/ready ] || exit 1" ]
