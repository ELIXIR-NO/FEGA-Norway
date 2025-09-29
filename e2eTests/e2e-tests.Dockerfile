# Use Temurin 21 as the base image for Java 21
FROM eclipse-temurin:25-jdk-alpine

# Install bash
RUN apk add --no-cache bash

# Set the working directory in the container
WORKDIR /fega-norway

# Copy the application JAR and scripts
COPY /build/libs/e2eTests.jar /fega-norway/e2eTests.jar
COPY env.sh /fega-norway/env.sh
COPY entrypoint.sh /entrypoint.sh

# Make entrypoint executable
RUN chmod +x /entrypoint.sh

# Run the entrypoint using bash
CMD ["/bin/bash", "/entrypoint.sh"]
