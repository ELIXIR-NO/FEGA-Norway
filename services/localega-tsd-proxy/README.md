# Local EGA TSD Proxy Service

The Local EGA TSD Proxy service is a component of the FEGA-Norway stack that facilitates secure file transfers between users and TSD (Services for Sensitive Data) storage. It implements authentication and authorization using both ELIXIR AAI and CEGA credentials, and manages file operations through the TSD File API.

## Features

- ELIXIR AAI (OpenID Connect) authentication support
- GA4GH Passport & Visa validation
- Secure file upload/download operations
- Resumable file transfers
- File operation event publishing to RabbitMQ
- Service health monitoring
- Redis-based caching
- PostgreSQL integration for credential mapping
- Serve the FEGA-Norway static web pages

## Prerequisites

- JDK 21
- Docker (for containerization)
- PostgreSQL database
- Redis instance
- RabbitMQ server with SSL support
- TSD API access credentials
- ELIXIR AAI client credentials
- CEGA authentication endpoint access

## Configuration

The service is configured through environment variables and the `application.yaml` file. Key configuration areas include:

### SSL Configuration

```yaml
server.ssl:
  enabled: ${SSL_ENABLED:true}
  key-store-type: PKCS12
  key-store: file:${SERVER_KEYSTORE_PATH:/etc/ega/ssl/server.cert}
  key-store-password: ${SERVER_CERT_PASSWORD}
```

### Database Configuration

```yaml
spring.datasource:
  url: jdbc:postgresql://${DB_INSTANCE:postgres}:${DB_PORT:5432}/${POSTGRES_DB:postgres}
  username: ${POSTGRES_USER:postgres}
  password: ${POSTGRES_PASSWORD}
```

### Redis Configuration

```yaml
spring.data.redis:
  host: ${REDIS_HOSTNAME:redis}
  port: ${REDIS_PORT:6379}
  database: ${REDIS_DB:0}
```

### RabbitMQ Configuration

```yaml
spring.rabbitmq:
  host: ${BROKER_HOST:public-mq}
  port: ${BROKER_PORT:5671}
  virtual-host: ${BROKER_VHOST:/}
  username: ${BROKER_USERNAME:admin}
  password: ${BROKER_PASSWORD:guest}
  ssl:
    enabled: ${BROKER_SSL_ENABLED:true}
```

## Building

The service uses Gradle as its build system. To build the service:

```bash
# Build the JAR
./gradlew build

# Build Docker image
./gradlew buildDockerImage
```

## Running

### Using Docker

```bash
docker run -p 8080:8080 \
  --env-file <path-to-env-file> \
  localega-tsd-proxy
```

### Using Java

```bash
java -jar build/libs/localega-tsd-proxy.jar
```

## API Endpoints

### Authentication

- `GET /token` - Retrieve access token
- `GET /user` - Retrieve user information

### File Operations

- `PATCH /stream/{fileName}` - Upload file
- `GET /stream/{fileName}` - Download file
- `GET /files` - List files
- `DELETE /files` - Delete file

### Resumable Uploads

- `GET /resumables` - List resumable uploads
- `DELETE /resumables` - Delete resumable upload

### Monitoring

- `GET /heartbeat` - Service health check

## Authentication Flow

1. Users authenticate using either:
   - ELIXIR AAI OpenID Connect
   - CEGA username/password
2. For ELIXIR AAI:
   - GA4GH Passports are validated
   - Visas are checked for access permissions
3. For CEGA:
   - Credentials are validated against CEGA auth endpoint
   - Password hashes are verified (BCrypt or crypt)
4. Upon successful authentication, EGA username is mapped to ELIXIR ID

## File Transfer Flow

1. Files are uploaded through resumable chunks
2. Each chunk's checksum is validated
3. Upon successful upload:
   - File metadata is recorded
   - Event is published to RabbitMQ
   - File is stored in TSD storage

## Security Features

- SSL/TLS encryption for all communications
- OAuth 2.0 / OpenID Connect integration
- GA4GH Passport & Visa validation
- Secure credential storage and validation
- Checksum verification for file integrity
- Secure file transfer protocols

## Static Web Pages

- The FEGA-Norway static web pages are maintained in a separate repository: [FEGA-Norway-webpages](https://github.com/ELIXIR-NO/FEGA-Norway-webpages)

## Development

### Project Structure

- `controllers/` - REST API endpoints
- `services/` - Business logic implementation
- `aspects/` - Cross-cutting concerns (AOP)
- `dto/` - Data transfer objects
- `authentication/` - Authentication components
- `config/` - Application configuration

### Adding New Features

When adding new features:

1. Follow the existing package structure
2. Implement appropriate tests
3. Use AOP for cross-cutting concerns
4. Update configuration as needed
5. Document changes in code

