FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

COPY . .

RUN ./gradlew :e2eTests:jar --no-daemon

FROM eclipse-temurin:25-jre-alpine

# Install bash
RUN apk add --no-cache bash

# Set the working directory in the container
WORKDIR /fega-norway

# Copy the application JAR, env, and scripts
COPY --from=builder /app/e2eTests/build/libs/e2eTests.jar /fega-norway/e2eTests.jar
COPY --from=builder /app/e2eTests/entrypoint.sh /fega-norway/entrypoint.sh

# Make entrypoint executable
RUN chmod +x /fega-norway/entrypoint.sh

# Run the entrypoint using bash
CMD ["/bin/bash", "/fega-norway/entrypoint.sh"]
