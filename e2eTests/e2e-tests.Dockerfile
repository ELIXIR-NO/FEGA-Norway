FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Layer 1: build infrastructure (rarely changes)
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
RUN ./gradlew :e2eTests:dependencies --no-daemon 2>&1 || true

# Layer 4: source code (changes frequently — only what this module needs)
COPY lib/crypt4gh/src/ lib/crypt4gh/src/
COPY e2eTests/src/ e2eTests/src/

RUN ./gradlew :e2eTests:jar --no-daemon

FROM golang:1.25-alpine AS lega-cmd-builder

WORKDIR /app

COPY cli/lega-commander/ .

RUN go build -o /lega-commander .

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache bash

WORKDIR /fega-norway

COPY --from=builder /app/e2eTests/build/libs/e2eTests.jar /fega-norway/e2eTests.jar
COPY --from=lega-cmd-builder /lega-commander /usr/local/bin/lega-commander
COPY e2eTests/entrypoint.sh /fega-norway/entrypoint.sh

RUN chmod +x /fega-norway/entrypoint.sh

CMD ["/bin/bash", "/fega-norway/entrypoint.sh"]
