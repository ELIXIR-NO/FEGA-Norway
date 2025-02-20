# Use a lightweight base image with necessary tools
FROM --platform=linux/amd64 eclipse-temurin:21-jdk-alpine

WORKDIR /storage

RUN apk add --no-cache bash

RUN mkdir -p "certs" && mkdir -p "confs"

COPY confs confs
COPY tmp_certs/* certs/
COPY scripts/* .

COPY .env .env

RUN chmod +x *.sh

ENTRYPOINT [ "/bin/sh", "-c", "chmod -R 777 /volumes && ./copy_certificates_to_dest.sh && ./copy_confs_to_dest.sh && ./replace_template_variables.sh && ./change_ownerships.sh && touch /storage/ready && tail -f /dev/null" ]

# Add a HEALTHCHECK to verify readiness
HEALTHCHECK --interval=5s --timeout=3s --retries=5 CMD [ "/bin/sh", "-c", "[ -f /storage/ready ] || exit 1" ]
