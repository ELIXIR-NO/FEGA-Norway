package main

import (
	"fmt"
	amqp "github.com/rabbitmq/amqp091-go"
	"slices"
)

type MockChannel struct {
	exchanges    map[string]any               // full configs for exchanges (nested maps)
	queues       map[string][]amqp.Publishing // maps queue name to list of messages posted there
	mappings     map[string]string            // maps "<exchange>:<routingKey>" to queue name
	altExchanges map[string]string            // maps exchange name to a configured alternate exchange
	exQueues     map[string][]string          // maps exchange name to a list of associated queues
	ack          bool
	nack         bool
}

// retrieve a string value from any level in a nested map with a list of nested attributes
func getFromMap(data map[string]any, keys []string) (string, bool) {
	if len(keys) == 0 {
		return "", false
	}
	value, ok := data[keys[0]]
	if !ok {
		return "", false
	}
	if len(keys) == 1 {
		str, ok := value.(string)
		if !ok {
			return "", false
		}
		return str, true
	}
	nested, ok := value.(map[string]any)
	if !ok {
		return "", false
	}
	return getFromMap(nested, keys[1:])
}

// create a mock channel with exchanges, queues and bindings defined in the provided configuration
func CreateMockChannel(config map[string]any) *MockChannel {
	ch := new(MockChannel)
	ch.exchanges = make(map[string]any)
	ch.queues = make(map[string][]amqp.Publishing)
	ch.mappings = make(map[string]string)
	ch.exQueues = make(map[string][]string)
	ch.altExchanges = make(map[string]string)
	exchanges := config["exchanges"].([]any)
	for _, exchange_list := range exchanges {
		exchange := exchange_list.(map[string]any)
		exchangeName := exchange["name"].(string)
		ch.exchanges[exchangeName] = exchange
		ch.exQueues[exchangeName] = make([]string, 0, 10)
		if altEx, ok := getFromMap(exchange, []string{"arguments", "alternate-exchange"}); ok {
			ch.altExchanges[exchangeName] = altEx
		}
	}
	bindings := config["bindings"].([]any)
	for _, binding_list := range bindings {
		binding := binding_list.(map[string]any)
		exchangeName := binding["source"].(string)
		routingKey := binding["routing_key"].(string)
		queueName := binding["destination"].(string)
		ch.mappings[exchangeName+":"+routingKey] = queueName
		ch.exQueues[exchangeName] = append(ch.exQueues[exchangeName], queueName)
	}
	return ch
}

// set an Ack flag for the channel. The flag can be checked later
func (ch *MockChannel) Ack(tag uint64, multiple bool) error {
	ch.ack = true
	return nil
}

// set a Nack flag for the channel. The flag can be checked later
func (ch *MockChannel) Nack(tag uint64, multiple bool, requeue bool) error {
	ch.nack = true
	return nil
}

// dummy implementation of Reject that doesn't do anything
func (ch *MockChannel) Reject(tag uint64, requeue bool) error {
	return nil
}

// add the message to queues determined by the exchange and routing key. (A queue is just a slice of messages)
func (ch *MockChannel) Publish(exchange, routingKey string, mandatory bool, immediate bool, msg amqp.Publishing) error {
    // check if exchange exists for this channel
    if _, ok := ch.exchanges[exchange]; !ok {
        return fmt.Errorf("Exchange does not exist: %s", exchange)
    }
	// check type of exchange (fanout or topic)
	exchangeConfig := ch.exchanges[exchange].(map[string]any)
	if exchangeType, ok := exchangeConfig["type"]; ok {
		if (exchangeType == "fanout") { // post message to all bound queues
			for _, queueName := range ch.exQueues[exchange] {
				ch.queues[queueName] = append(ch.queues[queueName], msg)
			}
			return nil
		}
	}
    // Exchange is not 'fanout', so we assume it is 'topic'
	// Find binding to queue
    queueName, ok := ch.mappings[exchange+":"+routingKey]
    if !ok {
        queueName = routingKey
    }
    // check if queue exists for the exchange
    queueExistsForExchange := slices.Contains(ch.exQueues[exchange], queueName)
    if queueExistsForExchange {
		// post message to queue bound to routing key
        ch.queues[queueName] = append(ch.queues[queueName], msg)
        return nil
    } else {
        // Queue does not exist. Check if an alternate exchange has been configured for this exchange
        if altEx, ok := ch.altExchanges[exchange]; ok {
			return ch.Publish(altEx, routingKey, mandatory, immediate, msg)
        } else {
            return fmt.Errorf("No queue could be found for routing key: %s\n", routingKey)
        }
    }
}

// pop and return the first message from the named queue (a queue is just a slice of messages)
func (ch *MockChannel) GetMessage(queue string) *amqp.Publishing {
	if len(ch.queues[queue]) > 0 {
		msg := ch.queues[queue][0]
		ch.queues[queue] = ch.queues[queue][1:]
		return &msg
	}
	return nil
}

// return the current Ack and Nack values for the channel and reset them afterwards
func (ch *MockChannel) GetAckNack() (bool, bool) {
	ack := ch.ack
	nack := ch.nack
	ch.ack = false
	ch.nack = false
	return ack, nack
}

// Clear all messages from queues and reset flags
func (ch *MockChannel) Clear() {
	for queueName := range ch.queues {
		ch.queues[queueName] = ch.queues[queueName][:0]
	}
	ch.ack = false
	ch.nack = false
}

