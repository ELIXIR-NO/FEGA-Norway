# Use Temurin 21 as the base image for Java 21
FROM eclipse-temurin:21-jdk-alpine

# Set the working directory in the container
WORKDIR /fega-norway

# Copy the compiled binaries and dependencies from your local machine into the container
COPY /build/libs/e2eTests.jar /fega-norway/e2eTests.jar
COPY .env /fega-norway/.env

# Set the entry point to run the tests using java -cp for classpath
# CMD ["java", "-cp", "/fega-norway/classes:/fega-norway/libs/*", "org.junit.platform.console.ConsoleLauncher", "--scan-classpath"]
#java -jar e2eTests.jar --select-class no.elixir.e2eTests.IngestionTest
CMD ["sleep", "infinity"]
