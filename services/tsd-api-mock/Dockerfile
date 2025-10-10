FROM gradle:8-jdk21 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle/ ./gradle/
COPY buildSrc ./buildSrc
COPY services/tsd-api-mock ./services/tsd-api-mock
RUN ./gradlew :services:tsd-api-mock:clean :services:tsd-api-mock:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN mkdir -p /etc/jwt/public_keys
WORKDIR /app
COPY --from=builder app/services/tsd-api-mock/build/libs/tsd-api-mock.jar /app/app.jar
EXPOSE 8080
CMD ["java","-XX:+UseG1GC", "-jar","app.jar"]
