# Use a base image with JDK 21 to run Java and Gradle
FROM eclipse-temurin:21-jdk AS builder

# Install Gradle
RUN apt-get update && \
    apt-get install -y wget unzip && \
    wget https://services.gradle.org/distributions/gradle-7.6-bin.zip -P /tmp && \
    unzip -d /opt/gradle /tmp/gradle-7.6-bin.zip && \
    rm /tmp/gradle-7.6-bin.zip

# Set the Gradle path
ENV PATH="/opt/gradle/gradle-7.6/bin:${PATH}"

# Set the working directory in the container
WORKDIR /app

# Copy the project files into the container
COPY . .

# Run the Gradle command to execute the tests
RUN gradle test

# Use a lighter JRE image for the final container
FROM eclipse-temurin:21-jre AS runner

# Copy the built files from the previous stage
COPY --from=builder /app /app

# Set the working directory in the container
WORKDIR /app

# Command to run tests on container start-up
CMD ["gradle", "test"]

