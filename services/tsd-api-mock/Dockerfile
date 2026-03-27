FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app


COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY buildSrc/ buildSrc/

# Layer 2: all module build files so Gradle can configure the project graph
COPY lib/clearinghouse/build.gradle.kts lib/clearinghouse/
COPY lib/tsd-file-api-client/build.gradle.kts lib/tsd-file-api-client/
COPY lib/crypt4gh/build.gradle.kts lib/crypt4gh/
COPY services/localega-tsd-proxy/build.gradle.kts services/localega-tsd-proxy/
COPY services/tsd-api-mock/build.gradle.kts services/tsd-api-mock/
COPY services/cega-mock/build.gradle.kts services/cega-mock/
COPY services/mq-interceptor/build.gradle.kts services/mq-interceptor/
COPY e2eTests/build.gradle.kts e2eTests/
RUN mkdir -p cli/lega-commander

# Layer 3: resolve dependencies (cached until build.gradle.kts changes)
RUN ./gradlew :services:tsd-api-mock:dependencies --no-daemon || true

# Layer 4: source code (changes frequently — only what this service needs)
COPY services/tsd-api-mock/src/ services/tsd-api-mock/src/

RUN ./gradlew :services:tsd-api-mock:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN mkdir -p /etc/jwt/public_keys
WORKDIR /app
COPY --from=builder /app/services/tsd-api-mock/build/libs/tsd-api-mock.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
