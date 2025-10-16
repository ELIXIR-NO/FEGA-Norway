# Start with the base image for Gradle and Java
FROM gradle:8-jdk21

WORKDIR /app

ARG GO_VERSION=1.25.2

# Install wget/tar, download, extract, and clean up
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    tar \
    && wget -q https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz -O go.tar.gz \
    && tar -C /usr/local -xzf go.tar.gz \
    && rm go.tar.gz \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set up the Go environment variables
ENV PATH="/usr/local/go/bin:${PATH}"

RUN go version

COPY . .

# Run your original Gradle command
RUN gradle clean build test --no-daemon --stacktrace