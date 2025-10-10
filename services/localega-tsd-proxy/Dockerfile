FROM gradle:8-jdk21 AS builder
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle/ ./gradle/
COPY buildSrc ./buildSrc
COPY lib/clearinghouse ./lib/clearinghouse
COPY lib/tsd-file-api-client ./lib/tsd-file-api-client
COPY services/localega-tsd-proxy ./services/localega-tsd-proxy
RUN ./gradlew :services:localega-tsd-proxy:clean :services:localega-tsd-proxy:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder app/services/localega-tsd-proxy/build/libs/localega-tsd-proxy.jar /app/app.jar
EXPOSE 8080
CMD ["java","-XX:+UseG1GC", "-jar","app.jar"]
