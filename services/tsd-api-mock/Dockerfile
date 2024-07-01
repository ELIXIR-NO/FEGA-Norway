FROM eclipse-temurin:21-jre-alpine
COPY build/libs/tsd-api-mock-*.jar /app/app.jar
CMD mkdir -p /etc/jwt/public_keys
WORKDIR /app
EXPOSE 8080
CMD ["java","-XX:+UseG1GC", "-jar","app.jar"]
