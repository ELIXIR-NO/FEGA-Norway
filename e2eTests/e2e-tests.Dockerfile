# Use Temurin 21 as the base image for Java 21
FROM eclipse-temurin:21-jdk-alpine As builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle/ ./gradle/
COPY buildSrc ./buildSrc
RUN ./gradlew :e2eTests:clean :e2eTests:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
# Install bash
RUN apk add --no-cache bash

# Set the working directory in the container
WORKDIR /fega-norway

# Copy the application JAR and scripts
COPY --from=builder app/build/libs/e2eTests.jar /fega-norway/e2eTests.jar
COPY env.sh /fega-norway/env.sh
COPY entrypoint.sh /entrypoint.sh

# Make entrypoint executable
RUN chmod +x /entrypoint.sh

# Run the entrypoint using bash
CMD ["/bin/bash", "/entrypoint.sh"]
