FROM ghcr.io/neicnordic/sensitive-data-archive:PR1293 AS base

# Use a more feature-complete base image for debugging
FROM ubuntu:20.04

# Copy the files from the original distroless image
COPY --from=base /usr/local/bin /usr/local/bin
COPY --from=base /frontend /frontend
COPY --from=base /schemas /schemas

ENTRYPOINT [ "/bin/sh", "-c", "tail -f /dev/null" ]

CMD ["sda-ingest"]
