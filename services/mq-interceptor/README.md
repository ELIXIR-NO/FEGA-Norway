<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
   <li><a href="#mq-interceptor">MQ-interceptor</a></li>
    <li>
      <a href="#how-it-works">How it works</a>
      <ul>
        <li><a href="#error-situations">Error situations</a></li>
      </ul>
    </li>
    <li><a href="#configuration">Configuration</a></li>
    <li><a href="#build-and-execute">Build and execute</a></li>
    <li><a href="#tests">Tests</a></li>
  </ol>
</details>

# MQ-interceptor

MQ-interceptor is a proxy service that transfers messages between two AMQP servers.

In the [setup proposed by EGA](https://localega.readthedocs.io/en/latest/amqp.html#connection-to-central-ega), messages are automatically passed between the Central EGA message server 
and a Local EGA (federated) message server via [federated queues](https://www.rabbitmq.com/docs/federation) or with the use of [shovels](https://www.rabbitmq.com/docs/shovel). 
However, in some environments, the local message server may not be allowed to connect directly to the central message server for security reasons, 
and it must therefore rely on a trusted proxy service to pass messages between the two MQ instances.
Another issue is that the credentials used to authenticate users against the Central EGA server may be different from those used on the Local EGA server.

The MQ-interceptor solves both of these problems by performing the following tasks:

1. It takes messages from the Central EGA message server and reposts them on the Local EGA message server, and vice versa.
2. Before reposting a message, it converts the "user" field in the message body from an identifier known to Central EGA to a different identifier type used by the Local EGA, or vice versa depending on the direction of the message.

The MQ-interceptor also checks that all the messages that it reposts are properly JSON-formatted, and it can optionally validate messages against [JSON schemas](https://github.com/EGA-archive/LocalEGA/tree/master/src/handler/schemas) depending on their type.

## How it works:

- MQ-interceptor first connects to a PostgreSQL database server to access a table containing mappings between EGA user IDs and Elixir user IDs.
- MQ-interceptor then connects to the Local MQ server and continuously consumes messages from the following queues: _inbox_, _verified_, _completed_ and _error_ (these names are hardcoded). The messages are reposted on the Central MQ server to the exchange configured by the `CEGA_MQ_EXCHANGE` environment variable. If the JSON-body of the message contains a "type" field, the new routing key will be based on this field. If not, the routing key will be set to the original queue name prefixed with "files." (hardcoded). For instance, messages read from the "verified" queue on the Local MQ server will be reposted with the routing key "files.verified" on the Central MQ server if they don’t have a "type" field.
- MQ-interceptor also connects to the Central MQ server and continuously consumes messages from the single queue configured with the `CEGA_MQ_QUEUE` environment variable (usually "v1.files"). These messages are reposted on the Local MQ server to the exchange configured by the `LEGA_MQ_EXCHANGE` variable with a new routing key based on the `LEGA_MQ_ROUTING_KEY` variable. (Note that these messages may later be reposted internally on the Local MQ server with new routing keys by a second interceptor service, such as [SDA-interceptor](https://github.com/neicnordic/sensitive-data-archive/blob/main/sda/cmd/intercept/intercept.md)).
- Before any message is reposted, the MQ-interceptor checks the JSON-formatted body of the message and replaces the value of the "user" field (if present) based on mappings obtained from the PostgreSQL database. For messages sent from CEGA to LEGA, the EGA user ID in the original message is replaced with an Elixir user ID ([Life Science Login](https://lifescience-ri.eu/ls-login/) ID), and vice versa for the opposite direction.
- The format of messages can optionally be validated against JSON schemas. If this validation fails (or if a message is not proper JSON at all), the message will not be reposted in the normal way, but instead be posted to the configured LEGA exchange with the routing key "validation_error".
- If a message was successfully reposted, an ACK (acknowledgement message) is posted back on the channel that the message was obtained from.
- If message processing fails for other reasons than JSON validation errors, a NACK (negative acknowledgement) is posted back on the channel that the message was obtained from (but the message is not requeued). Error messages are then posted to the CEGA exchange with routing key "files.error" and to the LEGA exchange with routing key "message_error".

### Error situations

The MQ-interceptor will exit with status code 1 if any of the following situations occur:
- It fails to connect to the PostgreSQL database
- It fails to read the directory specified by the `SCHEMA_FOLDER` environment variable (if this is set)
- It is unable to connect to either of the two MQ servers (after retrying 10 times) or fails to create channels on those servers for consume and publish.
- It fails to connect to any of the required queues:  _inbox_, _verified_, _completed_ and _error_ on the Local MQ and `CEGA_MQ_QUEUE` on the Central MQ.
- If fails to repost a message that fails JSON validation to the Local MQ with routing key "validation_error"
- It fails to convert a message "delivery" into a new "publish" object for reasons other than JSON validation errors _and_ …
    - it either fails to send a NACK (negative acknowledgement) back on the channel it got the original message from to signal that something went wrong
    - or it fails to publish error messages to either the Local or Central MQ
- It fails to repost a message (for reasons other than JSON validation errors) and also fails to send a NACK (negative acknowledgement) back on the channel it got the original message from to signal that something went wrong
- It succeeds in reposting the message but fails to send an ACK back on the channel it got the original message from to signal that everything went OK.

The MQ-interceptor will write error messages to the log but still continue to operate when any of the following situations occur:
- A message fails JSON validation, but the message was successfully reposted to the Local MQ with routing key "validation_error"
- It fails to convert a message "delivery" into a new "publish" object (for reasons other than JSON validation errors) but succeeds in reporting this problem by sending back a NACK signal on the same channel it got the original delivery from and also posting error messages to both the Local and Central MQs
- It fails to repost a message on the "publish" channel but succeeds in reporting this problem by sending back a NACK signal on the same channel it got the original delivery from

A JSON validation error will be raised if any of the following situations occur:
- A message is not in proper JSON format
- A JSON message that has been correctly parsed/unmarshalled and then possibly modified cannot be marshalled back into proper JSON
- Validation against JSON schemas is enabled and:
    - A message has a "type" field but the schema to use for this type is not known
    - A message has a "type" field but the schema for that type was not properly loaded
    - A message has a "type" field but it failed to validate against the schema for that type
    - A message does not have a "type" field and does not validate against any of the known schemas

The MQ-interceptor will otherwise fail to convert a "delivery" into a new "publish" object for reposting if any of the following situations occur:
- The value of the "user" field in the JSON-formatted body could not be converted because it is not found in the "mappings" table of the database (in the correct column depending on the direction).


## Configuration
The MQ-interceptor service can be configured by setting the following environment variables:

| Environment Variable | Description | 
| --- | --- |
| CEGA_MQ_CONNECTION | A connection string for connecting to the Central EGA message server.<br>`amqps://<user>:<password>@<host>:<port>/<vhost>[?parameters]` |
| CEGA_MQ_EXCHANGE | The name of the exchange to post messages to on the Central MQ server. Suggested value: "localega.v1" |
| CEGA_MQ_QUEUE | The name of the queue to read messages from on the Central MQ server. Suggested value: "v1.files" |
| LEGA_MQ_CONNECTION |  A connection string for connecting to the Local EGA message server.<br>`amqps://<user>:<password>@<host>:<port>/<vhost>[?parameters]` |
| LEGA_MQ_EXCHANGE | The name of the exchange to post messages to on the Local MQ server. Suggested value: "sda" |
| LEGA_MQ_ROUTING_KEY | The name of the queue in the Local MQ server that messages coming from Central MQ should be forwarded to. This value is used as the routing key. Suggested value: "files" |
| POSTGRES_CONNECTION | A connection string for the PostgreSQL database containing the user ID mappings.<br>`postgres://[username]:[password]@[host]:5432/[database]` | 
| ENABLE_TLS | Enables TLS for the AMQP connection. When set to `true`, the interceptor connects to RabbitMQ using `amqps://` instead of `amqp://` (default) |
| DEBUG | Enables verbose TLS diagnostic logging (certificate pool details, CA path resolution) when set to `true` |
| CA_CERT_PATH | Path to a custom CA certificate (PEM) used to verify the broker's TLS certificate. If unset, the system certificate pool is used. Example value: "/etc/ssl/certs/ca.pem" |
| SCHEMA_FOLDER | Path to a directory containing JSON schemas for message validation. If unset, messages will not be validated except to check that they are in proper JSON format. |

Note that none of the CEGA_\*, LEGA_\* and POSTGRES_\* variables have default fallback values, and MQ-interceptor will usually fail to start if they are not set explicitly.

## Build and execute
The MQ-interceptor is written in the [Go language](https://go.dev/) and requires the [Go compiler](https://go.dev/doc/install) to build.
```bash
go build
```
This will create an executable file named "mq-interceptor" in the same directory.

To build with [Gradle](https://gradle.org/), run the command
```bash
gradle build
```
This will create the executable "mq-interceptor" inside the "build" subdirectory.

To build a Docker image containing the MQ-interceptor, run the command
```bash
docker build -t mq-interceptor .
```

## Tests
To run the [unit tests](test/README.md), run the command
```bash
go test -v
```
or with Gradle:
```bash
gradle test
```


