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

## How it works:

- MQ-interceptor first connects to a PostgreSQL database server to access a table containing mappings between EGA user IDs and Elixir user IDs.
- MQ-interceptor then connects to the Local MQ server and continuously consumes messages from the following queues: _inbox_, _verified_, _completed_ and _error_ (these names are hardcoded). The messages are reposted on the Central MQ server to the exchange configured by the `CEGA_MQ_EXCHANGE` environment variable. If the JSON-body of the message contains a "type" field, the new routing key will be based on this field. If not, the routing key will be set to the original queue name prefixed with "files." (hardcoded). For instance, messages read from the "verified" queue on the Local MQ server will be reposted with the routing key "files.verified" on the Central MQ server if they don’t have a "type" field.
- MQ-interceptor also connects to the Central MQ server and continuously consumes messages from the single queue configured with the `CEGA_MQ_QUEUE` environment variable (usually "v1.files"). These messages are reposted on the Local MQ server to the exchange configured by the `LEGA_MQ_EXCHANGE` variable with a new routing key based on the `LEGA_MQ_ROUTING_KEY` variable. (Note that these messages may later be reposted internally on the Local MQ server with new routing keys by a second interceptor service, such as [SDA-interceptor](https://github.com/neicnordic/sensitive-data-archive/blob/main/sda/cmd/intercept/intercept.md)).
- Before any message is reposted, the MQ-interceptor checks the JSON-formatted body of the message and replaces the value of the "user" field (if present) based on mappings obtained from the PostgreSQL database. For messages sent from CEGA to LEGA, the EGA user ID in the original message is replaced with an Elixir user ID ([Life Science Login](https://lifescience-ri.eu/ls-login/) ID), and vice versa for the opposite direction.
- If a message was successfully reposted, an ACK (acknowledgement message) is posted back on the channel that the message was obtained from.
- If a message could not be successfully converted or reposted, a NACK (negative acknowledgement) is posted back on the channel that the message was obtained from. If the message could not be converted, an error message is also posted to the error channel (`CEGA_MQ_EXCHANGE` with routing key "files.error")

### Error situations

The MQ-interceptor will exit with status code 1 if any of the following situations occur:
- It is unable to connect to either of the two MQ servers (after retrying 10 times) or fails to create channels on those servers for consume and publish.
- It fails to connect to any of the required queues:  _inbox_, _verified_, _completed_ and _error_ on the Local MQ and `CEGA_MQ_QUEUE` on the Central MQ.
- It fails to convert a message "delivery" into a new "publish" object _and_ …
    - it either fails to send a NACK (negative acknowledgement) back on the channel it got the original message from to signal that something went wrong
    - or it fails to publish an error message on the error channel
- It fails to repost a message and also fails to send a NACK (negative acknowledgement) back on the channel it got the original message from to signal that something went wrong
- It succeeds in reposting the message but fails to send an ACK back on the channel it got the original message from to signal that everything went OK.

The MQ-interceptor will write error messages to the log but still continue to operate when any of the following situations occur:
- It fails to convert a message "delivery" into a new "publish" object for repost but succeeds in reporting this problem by sending back a NACK signal on the same channel it got the original delivery from and also posting an error message on the error channel
- It fails to repost a message on the "publish" channel but succeeds in reporting this problem by sending back a NACK signal on the same channel it got the original delivery from

The MQ-interceptor will fail to convert a "delivery" to a new "publish" object for reposting if any of the following situations occur:
- The body of the message could not be parsed as JSON 
- The value of the "user" field in the JSON-formatted body could not be converted because it is not found in the "mappings" table of the database (in the correct column depending on the direction).
-  The message could not be reformatted in JSON

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
| VERIFY_CERT | If this is set to FALSE, the client will not verify the servers’ certificate chain and host name and will thus accept any certificate presented by the server and any host name in that certificate. This should only be set to FALSE for testing purposes. |

None of these variables have default fallback values, and MQ-interceptor will usually fail to start if they are not set explicitly. The only variable that is not required is VERIFY_CERT, and the MQ-interceptor will always verify certificates unless this variable is explicitly set to "false".

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
To run the [unit tests](main_test.go), run the command
```bash
go test -v
```
or with Gradle:
```bash
gradle test
```


