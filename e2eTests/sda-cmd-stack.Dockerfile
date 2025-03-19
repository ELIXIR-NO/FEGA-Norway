# This Dockerfile is for debugging purposes.
# To exec into certain SDA images, replace the base image.
# The rest of the multi-stage build will allow you to exec into it.
FROM ghcr.io/neicnordic/sensitive-data-archive:PR1293 AS base

# Use a more feature-complete base image for debugging
FROM ubuntu:24.04

# Copy the files from the original distroless image
COPY --from=base /usr/local/bin /usr/local/bin
COPY --from=base /frontend /frontend
COPY --from=base /schemas /schemas

ENTRYPOINT [ "/bin/sh", "-c", "tail -f /dev/null" ]

CMD ["sda-ingest"]
