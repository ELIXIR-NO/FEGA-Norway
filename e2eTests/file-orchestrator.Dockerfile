# Use a lightweight base image with necessary tools
FROM eclipse-temurin:21-jdk-alpine

ARG MKCERT_VERSION="v1.4.4"
ARG CRYPT4GH_VERSION="v1.14.0"
ARG LOCAL_BIN="/usr/local/bin"

WORKDIR /storage

RUN apk update && apk add --no-cache bash openssl curl

# Install mkcert (arch-aware: the container runs on the host architecture)
RUN set -eux; \
    case "$(uname -m)" in \
      x86_64) arch=amd64 ;; \
      aarch64 | arm64) arch=arm64 ;; \
      *) echo "unsupported arch: $(uname -m)" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://github.com/FiloSottile/mkcert/releases/download/${MKCERT_VERSION}/mkcert-${MKCERT_VERSION}-linux-${arch}" -o "${LOCAL_BIN}/mkcert"; \
    chmod +x "${LOCAL_BIN}/mkcert"

# Install crypt4gh (pinned; download the release tarball directly because the
# upstream install.sh rejects linux/arm64). Asset arch: x86_64 / arm64.
RUN set -eux; \
    case "$(uname -m)" in \
      x86_64) c4arch=x86_64 ;; \
      aarch64 | arm64) c4arch=arm64 ;; \
      *) echo "unsupported arch: $(uname -m)" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://github.com/neicnordic/crypt4gh/releases/download/${CRYPT4GH_VERSION}/crypt4gh_linux_${c4arch}.tar.gz" \
      | tar -xz -C "${LOCAL_BIN}" crypt4gh; \
    chmod +x "${LOCAL_BIN}/crypt4gh"

RUN mkdir -p "confs"
RUN mkdir -p "certs"
RUN mkdir -p "schemas"

COPY confs confs
COPY scripts/ .

COPY "env.sh" "env.sh"

RUN chmod +x *.sh

ENTRYPOINT [ "./entrypoint.sh" ]

# Add a HEALTHCHECK to verify readiness
HEALTHCHECK --interval=5s --timeout=3s --retries=5 CMD [ "/bin/sh", "-c", "[ -f /storage/ready ] || exit 1" ]
