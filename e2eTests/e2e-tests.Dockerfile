# Use Temurin 21 as the base image for Java 21
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle/ ./gradle/
COPY buildSrc ./buildSrc
COPY lib/crypt4gh ./lib/crypt4gh
COPY e2eTests/ ./e2eTests

RUN ./gradlew :lib:crypt4gh:build :e2eTests:jar --no-daemon

FROM eclipse-temurin:21-jre-alpine

# Install bash
RUN apk add --no-cache bash

# Set the working directory in the container
WORKDIR /fega-norway

# Copy the application JAR, env, and scripts
COPY --from=builder /app/e2eTests/build/libs/e2eTests.jar /fega-norway/e2eTests.jar
COPY --from=builder /app/e2eTests/env.sh /fega-norway/env.sh
COPY --from=builder /app/e2eTests/entrypoint.sh /fega-norway/entrypoint.sh

# Make entrypoint executable
RUN chmod +x /fega-norway/entrypoint.sh

# Run the entrypoint using bash
CMD ["/bin/bash", "/fega-norway/entrypoint.sh"]
