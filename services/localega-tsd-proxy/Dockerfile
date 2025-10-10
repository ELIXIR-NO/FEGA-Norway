ARG BUILDER_IMAGE=gradle:8-jdk21

FROM ${BUILDER_IMAGE} AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY buildSrc ./buildSrc
COPY lib/clearinghouse ./lib/clearinghouse
COPY lib/tsd-file-api-client ./lib/tsd-file-api-client
COPY services/localega-tsd-proxy ./services/localega-tsd-proxy

RUN gradle clean :services:localega-tsd-proxy:assemble --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY --from=builder app/services/localega-tsd-proxy/build/libs/localega-tsd-proxy.jar /app/app.jar

EXPOSE 8080

CMD ["java","-XX:+UseG1GC", "-jar","app.jar"]
