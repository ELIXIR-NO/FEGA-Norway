FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY . .
RUN ./gradlew :services:localega-tsd-proxy:clean :services:localega-tsd-proxy:build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder app/services/localega-tsd-proxy/build/libs/localega-tsd-proxy.jar /app/app.jar
EXPOSE 8080
CMD ["java","-XX:+UseG1GC", "-jar","app.jar"]
