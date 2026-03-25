# Unit tests for MQ-interceptor

Unit tests defined in [main_test.go](../main_test.go) cover six functions from `main.go`. They perform the following checks:

`Test_selectElixirIdByEGAId()` + `Test_selectEgaIdByElixirId()`
- Check that known users are mapped correctly and no error is returned
- Check that unknown users map to an empty string and an error is returned

`Test_publishError()`
- Check that the posted error message was routed correctly and can be retrived from the expected queue
- Check that the error message has the expected contents (a field with the original error and a field with the body of the original message)

`Test_postMessage()`
- Check that the posted message was routed correctly and can be retrived from the expected queue
- Check that the message has the expected contents
- Check that a message posted to LEGA with unrecognized routing key eventually ends up in the "dropped_messages" queue. This also confirms that the mock LEGA MQ server configuration and behaviour is correct.

`Test_buildPublishingFromDelivery()`
- Check that tests that are designed to fail return errors
- Check that tests that are designed to succeed return a message (publishing) but no error
- Check that the value of the "user" field is replaced correctly (depending on the direction of the message)
- Check that the message type returned by the function matches the type in the message itself (if present)

`Test_forwardDeliveryTo()`
- If delivery of a message is expected to succeed:
  - Check that the forwarded message ended up in the correct queue
  - Check that the forwarded message has the expected contents (including correct user mapping)
  - Check that it was correctly Ack'ed
  - Check that it was _not_ Nack'ed
  - Check that no error messages were posted to either CEGA or LEGA
- If delivery of a message was designed to fail for a given test:
  - Check that the message was _not_ sent to the regular queue 
  - Check that the message was _not_ Ack'ed
  - If the message delivery failed due to JSON validation error:
    - Check that the message was reposted to LEGA and ended up in the "dropped_messages" queue
    - Check that the message retrieved from the "dropped_messages" queue matches the original message
    - Check that no error message was posted to CEGA
  - If the message delivery failed due to other errors:
    - Check that the an error message was posted to the CEGA error channel
    - Check that the an error message was posted to LEGA and ended up in the "dropped_messages" queue
    - Check that the message was Nack'ed


The two functions `buildPublishingFromDelivery` and `forwardDeliveryTo` are tested by looping through a suite of messages defined in [test/tests.json](tests.json). Some of these are designed to fail for various reasons, such as JSON validation errors or unknown users. The tests include messages going in both directions, and messages are validated against schemas in the [test/schemas](schemas) directory.  This directory deliberately only contains three schemas, both to save space and also to check what happens when a required schema is missing.

The test suite covers these scenarios:
1. test successful processing and delivery of a message of type "ingest"
2. test successful processing and delivery of a message of type "accession"
3. test successful processing and delivery of a message without an explicit type (which is valid against at least one of the known schemas)
4. test failure respons when a valid message contains an unknown user
5. test failure respons when a typed message fails to validate against its JSON schema (missing required "user" field)
6. test failure respons when a typed message fails to validate against its JSON schema (value of SHA256 checksum does not have correct format)
7. test failure respons when a message fails to validate because it is not properly JSON-formatted at all (but actually HTML)
8. test failure respons when a typed message fails to validate because the schema for that specific type is missing
9. test failure respons when an untyped message fails to validate against any of the known schemas

### Format of the `test/tests.json` file.

The test suite file contains a list of JSON objects that define tests that should be run by forwarding different messages.

Each test is defined with the following properties (not all are necessarily required):

|Name|Type|Description|
|---|---|---|
|testname|string| A name for the test. This is displayed when tests are run in verbose mode.|
|description|any| A human-readable description of the test|
|direction|string| A designation for the MQ server that the message should be posted to. Valid directions are "cega" or "lega". The name of the exchange to post messages to will be determined based on this value and the environment variables that are set.|
|routingkey|string| The routing key to use for messages sent to "cega". For messages to "lega", the MQ-interceptor will always use the same routing key ("files").|
|queue|string| The name of the queue where the message is expected to end up if the forwarding was successful|
|fails|boolean| If TRUE, it means that the test simulates a condition where the regular message forwarding is expected to fail, for instance due to a message validation error.|
|cause|string| The type of error expected if "fails" is true. If this string starts with "validation", the test should fail due to a ValidationError.|
|reason|string| A human-readable description of the reason why the test is expected to fail (if "fails" is true)|
|enduser|string| The expected value of the "user" field in the message after it has been reposted by the MQ-interceptor|
|message|object| The actual message to forward, usually in the form of a JSON object. (Could also be a string if it is not valid JSON)|




## Implementation of tests

For the purpose of unit testing, the PostgreSQL database and RabbitMQ servers for CEGA and LEGA are simulated using mock instances.

### Mock database

The database containing username mappings is simulated using [RamSQL](https://pkg.go.dev/github.com/proullon/ramsql), a simple in-memory SQL database. 
The test suite creates a small "mapping" table with 3 users: alice, bob and carol.

### Mock RabbitMQ servers

The MQ-interceptor connects to the CEGA and LEGA MQ servers and obtains "channels" used to _consume_ (read messages from queues) and _publish_ (post messages to exchanges) for each server. The unit tests use `MockChannel` (defined in [mock_amqp.go](../mock_amqp.go)) to simulate these connections. Both MockChannel and the [Channel](https://github.com/rabbitmq/amqp091-go/blob/main/channel.go) from the "official" RabbitMQ library (amqp091-go) implement the interface MQChannel (in `main.go`) which defines 4 functions that are required by both: `Publish`, `Ack`, `Nack` and `Reject`.

The MockChannel's Reject method is not really needed for anything useful, so its implementation is empty. The Ack and Nack methods just raise flags that can be checked later (they do not support "requeuing"). The MockChannel's `Publish(exchange, key, mandatory, immediate, message)` method will "publish" the message to an "exchange" with the given routing "key" and make sure the message ends up in the right "queue" (which is just an array with messages) based on the configuration of the RabbitMQ server. The message can thereafter be retrieved from the queue with MockChannel's `getMessage(queueName)` method and inspected. (The "mandatory" and "immediate" parameters of the Publish method are ignored).

A new MockChannel can be created with the `CreateMockChannel(config map[string]any)` method, which takes a full description of the RabbitMQ server configuration as input in the form of a map. This configuration is assumed to be based on a [definition.json](https://www.rabbitmq.com/docs/definitions) file. The test-directory includes definitions to set up the mock CEGA ([test/cegamq.json](cegamq.json)) and LEGA ([test/legamq.json](legamq.json)) MQ servers (based on their actual configurations), but not all types of definitions are supported by MockChannel.

To greatly simplify things, MockChannel only recognizes these definitions:

|Property|Comment|
|---|---|
|`exchanges`|Recognized sub-properties are `name`, `type` and `"arguments": {"alternate-exchange": "<name>"}`. If the "type" has value "fanout", messages will be posted to all queues bound to the exchange, irrespective of the routing key. All exchanges that are not "fanout" are assumed to be "topic" exchanges, and messages will then be routed based on the exact value of the routing key (wildcards `*` and `#` are not supported).|
|`queues`| Only the `name` of the queue is used. |
|`bindings`| The `source` (exchange), `destination` (queue name) and `routing_key` are used to bind the queue to the exchange and map the routing key to it.|
