FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

COPY . .
RUN ./gradlew :services:tsd-api-mock:clean :services:tsd-api-mock:build -x test --no-daemon

FROM eclipse-temurin:25-jre-alpine
RUN mkdir -p /etc/jwt/public_keys
WORKDIR /app
COPY --from=builder app/services/tsd-api-mock/build/libs/tsd-api-mock.jar /app/app.jar
EXPOSE 8080
CMD ["java","-XX:+UseG1GC", "-jar","app.jar"]
