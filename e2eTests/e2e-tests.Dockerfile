FROM gradle:8-jdk21 AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY buildSrc ./buildSrc
COPY lib/crypt4gh ./lib/crypt4gh
COPY e2eTests/ ./e2eTests

RUN gradle clean :lib:crypt4gh:assemble :e2eTests:jar --no-daemon

# Use JDK instead of JRE because the entrypoint requires
# 'keytool' to import certificates at runtime
FROM eclipse-temurin:21-jdk-alpine

# Install bash
RUN apk add --no-cache bash tree

# Set the working directory in the container
WORKDIR /fega-norway

# Copy the application JAR, env, and scripts
COPY --from=builder /app/e2eTests/build/libs/e2eTests.jar /fega-norway/e2eTests.jar
COPY --from=builder /app/e2eTests/env.sh /fega-norway/env.sh
COPY --from=builder /app/e2eTests/scripts/e2e-tests-entrypoint.sh /fega-norway/entrypoint.sh

# Make entrypoint executable
RUN chmod +x /fega-norway/entrypoint.sh

# Run the entrypoint using bash
CMD ["/bin/bash", "/fega-norway/entrypoint.sh"]
