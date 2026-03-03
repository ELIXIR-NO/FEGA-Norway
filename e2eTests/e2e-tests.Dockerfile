FROM eclipse-temurin:25-jdk-alpine AS builder

# imply FEGA-Norway monorepo root
WORKDIR /FEGA-Norway

# copy the entire monorepo
COPY . .

RUN ./gradlew e2eTests:jar

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN apk add --no-cache bash

COPY --from=builder /FEGA-Norway/e2eTests/build/libs/e2eTests.jar .
COPY --from=builder /FEGA-Norway/e2eTests/env.sh .
COPY --from=builder /FEGA-Norway/e2eTests/entrypoint.sh .

RUN chmod +x /app/entrypoint.sh

CMD ["/bin/bash", "/app/entrypoint.sh"]
