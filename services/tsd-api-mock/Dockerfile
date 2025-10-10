ARG BUILDER_IMAGE=gradle:8-jdk21

FROM ${BUILDER_IMAGE} AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY buildSrc ./buildSrc
COPY services/tsd-api-mock ./services/tsd-api-mock

RUN gradle clean :services:tsd-api-mock:assemble --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN mkdir -p /etc/jwt/public_keys

WORKDIR /app

COPY --from=builder app/services/tsd-api-mock/build/libs/tsd-api-mock.jar /app/app.jar

EXPOSE 8080

CMD ["java","-XX:+UseG1GC", "-jar","app.jar"]
