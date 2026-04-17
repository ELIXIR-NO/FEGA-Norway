// Package main contains the main logic of the "mq-interceptor" microservice.
package main

import (
	"crypto/tls"
	"crypto/x509"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/ELIXIR-NO/FEGA-Norway/mq-interceptor/validator"
	_ "github.com/lib/pq"
	amqp "github.com/rabbitmq/amqp091-go"
	"log"
	"net/url"
	"os"
	"sync"
	"time"
)

// This interface serves as the supertype for both amqp.Channel and the MockChannel used for testing
type MQChannel interface {
	Ack(tag uint64, multiple bool) error
	Nack(tag uint64, multiple bool, requeue bool) error
	Reject(tag uint64, requeue bool) error
	Publish(exchange, key string, mandatory, immediate bool, msg amqp.Publishing) error
}

type Bridge struct {
	CEGAConsumeChannel MQChannel
	CEGAPublishChannel MQChannel
	CEGAErrorChannel   MQChannel
	CEGAExchange       string

	LEGAConsumeChannel MQChannel
	LEGAPublishChannel MQChannel
	LEGAErrorChannel   MQChannel
	LEGAExchange       string
}

func (b *Bridge) getConsumeChannel(fromCEGAToLEGA bool) MQChannel {
	if fromCEGAToLEGA {
		return b.CEGAConsumeChannel
	} else {
		return b.LEGAConsumeChannel
	}
}

func (b *Bridge) getPublishChannel(fromCEGAToLEGA bool) MQChannel {
	if fromCEGAToLEGA {
		return b.LEGAPublishChannel
	} else {
		return b.CEGAPublishChannel
	}
}

func (b *Bridge) getPublishExchange(fromCEGAToLEGA bool) string {
	if fromCEGAToLEGA {
		return b.LEGAExchange
	} else {
		return b.CEGAExchange
	}
}

var db *sql.DB
var publishMutex sync.Mutex
var jsonValidator *validator.JSONValidator

// dialRabbitMQ attempts to connect to RabbitMQ up to 10 times
// with a delay between retries. It returns a connection
// instance or an error.
func dialRabbitMQ(connectionString string) (*amqp.Connection, error) {
	var conn *amqp.Connection
	var err error
	var attempts = 10
	// Parse the connection string as a URL.
	u, err := url.Parse(connectionString)
	if err != nil {
		return nil, err
	}
	log.Printf("Trying to dial host: %s [I will attempt to dial %d times with 10 seconds interval]", u.Hostname(), attempts)
	for i := 0; i < attempts; i++ {
		if os.Getenv("ENABLE_TLS") == "true" {
			conn, err = amqp.DialTLS(connectionString, getTLSConfig())
		} else {
			conn, err = amqp.Dial(connectionString)
		}
		if err == nil {
			log.Printf("Successfully connected to host %s\n", u.Hostname())
			return conn, nil
		}
		log.Printf("Attempt %d: Failed to connect to RabbitMQ: %s\n", i+1, err)
		time.Sleep(10 * time.Second) // Wait before retrying
	}
	// After all attempts, return the last error
	return nil, err
}

func main() {

	var err error

	db, err = sql.Open("postgres", os.Getenv("POSTGRES_CONNECTION"))
	failOnError(err, "Failed to connect to DB")

	schemafolder := os.Getenv("SCHEMA_FOLDER")
	jsonValidator = validator.NewJSONValidator(schemafolder)

	log.Printf("Is TLS enabled? %t", os.Getenv("ENABLE_TLS") == "true")

	legaMqConnString := os.Getenv("LEGA_MQ_CONNECTION")
	legaMQ, err := dialRabbitMQ(legaMqConnString)
	failOnError(err, "Failed to connect to LEGA queue after many attempts")
	legaConsumeChannel, err := legaMQ.Channel()
	failOnError(err, "Failed to create LEGA consume RabbitMQ channel")
	legaPublishChannel, err := legaMQ.Channel()
	failOnError(err, "Failed to create LEGA publish RabbitMQ channel")
	legaNotifyCloseChannel := legaMQ.NotifyClose(make(chan *amqp.Error))
	go func() {
		err := <-legaNotifyCloseChannel
		log.Fatal(err)
	}()

	cegaMqConnString := os.Getenv("CEGA_MQ_CONNECTION")
	cegaMQ, err := dialRabbitMQ(cegaMqConnString)
	failOnError(err, "Failed to connect to CEGA queue after many attempts")
	cegaConsumeChannel, err := cegaMQ.Channel()
	failOnError(err, "Failed to create CEGA consume RabbitMQ channel")
	cegaPublishChannel, err := cegaMQ.Channel()
	failOnError(err, "Failed to create CEGA publish RabbitMQ channel")
	cegaNotifyCloseChannel := cegaMQ.NotifyClose(make(chan *amqp.Error))
	go func() {
		err := <-cegaNotifyCloseChannel
		log.Fatal(err)
	}()

	var bridge *Bridge = &Bridge{
		CEGAConsumeChannel: cegaConsumeChannel,
		CEGAPublishChannel: cegaPublishChannel,
		CEGAErrorChannel:   cegaPublishChannel,
		CEGAExchange:       os.Getenv("CEGA_MQ_EXCHANGE"),
		LEGAConsumeChannel: legaConsumeChannel,
		LEGAPublishChannel: legaPublishChannel,
		LEGAErrorChannel:   legaPublishChannel,
		LEGAExchange:       os.Getenv("LEGA_MQ_EXCHANGE"),
	}

	cegaQueue := os.Getenv("CEGA_MQ_QUEUE")
	cegaDeliveries, err := cegaConsumeChannel.Consume(cegaQueue, "", false, false, false, false, nil)
	failOnError(err, "Failed to connect to CEGA queue: "+cegaQueue)
	go func() {
		for delivery := range cegaDeliveries {
			forwardDeliveryTo(true, bridge, "", delivery)
		}
	}()

	errorDeliveries, err := legaConsumeChannel.Consume("error", "", false, false, false, false, nil)
	failOnError(err, "Failed to connect to 'error' queue")
	go func() {
		for delivery := range errorDeliveries {
			forwardDeliveryTo(false, bridge, "files.error", delivery)
		}
	}()

	verifiedDeliveries, err := legaConsumeChannel.Consume("verified", "", false, false, false, false, nil)
	failOnError(err, "Failed to connect to 'verified' queue")
	go func() {
		for delivery := range verifiedDeliveries {
			forwardDeliveryTo(false, bridge, "files.verified", delivery)
		}
	}()

	completedDeliveries, err := legaConsumeChannel.Consume("completed", "", false, false, false, false, nil)
	failOnError(err, "Failed to connect to 'completed' queue")
	go func() {
		for delivery := range completedDeliveries {
			forwardDeliveryTo(false, bridge, "files.completed", delivery)
		}
	}()

	inboxDeliveries, err := legaConsumeChannel.Consume("inbox", "", false, false, false, false, nil)
	failOnError(err, "Failed to connect to 'inbox' queue")
	go func() {
		for delivery := range inboxDeliveries {
			forwardDeliveryTo(false, bridge, "files.inbox", delivery)
		}
	}()

	forever := make(chan bool)
	log.Printf(" [*] Waiting for messages. To exit press CTRL+C")
	<-forever
}

func forwardDeliveryTo(fromCEGAToLEGA bool, bridge *Bridge, routingKey string, delivery amqp.Delivery) {
	publishMutex.Lock()
	defer publishMutex.Unlock()

	channelFrom := bridge.getConsumeChannel(fromCEGAToLEGA)
	channelTo   := bridge.getPublishChannel(fromCEGAToLEGA)
	exchange    := bridge.getPublishExchange(fromCEGAToLEGA)

	publishing, messageType, err := buildPublishingFromDelivery(fromCEGAToLEGA, delivery)
	if err != nil {
		log.Printf("%s", err)
		var valErr validator.ValidationError
		if errors.As(err, &valErr) { // message failed JSON validation
			// send messages that fail validation to LEGA with routing key "validation_error"
			ackError := channelFrom.Ack(delivery.DeliveryTag, false)
			failOnError(ackError, "Failed to Ack message")
			err = postMessage(*publishing, bridge.LEGAPublishChannel, bridge.LEGAExchange, "validation_error")
			failOnError(err, "Failed to drop message that failed JSON validation")
		} else { // for other errors, post an error message back to CEGA (and also to LEGA)
			nackError := channelFrom.Nack(delivery.DeliveryTag, false, false)
			failOnError(nackError, "Failed to Nack message")
			errPub := publishError(delivery, err, bridge.CEGAErrorChannel, bridge.CEGAExchange, "files.error")
			failOnError(errPub, "Failed to publish error message to CEGA")
			errPub = publishError(delivery, err, bridge.LEGAErrorChannel, bridge.LEGAExchange, "message_error")
			failOnError(errPub, "Failed to publish error message to LEGA")
		}
		return
	}
	// Forward all messages from CEGA to a local queue handled by the SDA intercept service
	if fromCEGAToLEGA {
		routingKey = os.Getenv("LEGA_MQ_ROUTING_KEY")
	} else if messageType != nil {
		routingKey = messageType.(string)
	}
	err = channelTo.Publish(exchange, routingKey, false, false, *publishing)
	if err != nil {
		log.Printf("%s", err)
		err = channelFrom.Nack(delivery.DeliveryTag, false, true)
		failOnError(err, "Failed to Nack message")
	} else {
		err = channelFrom.Ack(delivery.DeliveryTag, false)
		failOnError(err, "Failed to Ack message")
		log.Printf("Forwarded message from [%s, %s] to [%s, %s]", delivery.Exchange, delivery.RoutingKey, exchange, routingKey)
		log.Printf("Correlation ID: %s", delivery.CorrelationId)
		log.Printf("Message: %s", string(delivery.Body))
	}
}

func buildPublishingFromDelivery(fromCEGAToLEGA bool, delivery amqp.Delivery) (*amqp.Publishing, interface{}, error) {
	publishing := amqp.Publishing{
		Headers:         delivery.Headers,
		ContentType:     delivery.ContentType,
		ContentEncoding: delivery.ContentEncoding,
		DeliveryMode:    delivery.DeliveryMode,
		Priority:        delivery.Priority,
		CorrelationId:   delivery.CorrelationId,
		ReplyTo:         delivery.ReplyTo,
		Expiration:      delivery.Expiration,
		MessageId:       delivery.MessageId,
		Timestamp:       delivery.Timestamp,
		Type:            delivery.Type,
		UserId:          delivery.UserId,
		AppId:           delivery.AppId,
		Body:            delivery.Body,
	}

	message := make(map[string]interface{}, 0)
	err := json.Unmarshal(delivery.Body, &message)
	if err != nil {
		// return ValidationError if the message is not proper JSON
		return &publishing, nil, validator.ValidationError{Message: "Message is not valid JSON", SchemaError: err}
	}

	validationError := jsonValidator.Validate(message)
	if validationError != nil {
		return &publishing, nil, validationError
	}

	messageType, _ := message["type"]

	user, ok := message["user"]
	if !ok {
		return &publishing, messageType, nil
	}

	stringUser := fmt.Sprintf("%s", user)

	if fromCEGAToLEGA {
		elixirId, err := selectElixirIdByEGAId(stringUser)
		if err != nil {
			return nil, "", err
		}
		message["user"] = elixirId
	} else {
		egaId, err := selectEgaIdByElixirId(stringUser)
		if err != nil {
			return nil, "", err
		}
		message["user"] = egaId
	}

	publishing.Body, err = json.Marshal(message)
	if err != nil {
		errMsg := fmt.Sprintf("Unable to convert message to JSON: %s", err)
		return &publishing, messageType, validator.ValidationError{Message: errMsg}
	}

	return &publishing, messageType, nil
}

func publishError(delivery amqp.Delivery, err error, errorChannel MQChannel, exchange string, routingKey string) error {
	errorMessage := fmt.Sprintf("{\"reason\" : \"%s\", \"original_message\" : \"%s\"}", err.Error(), string(delivery.Body))
	publishing := amqp.Publishing{
		ContentType:     delivery.ContentType,
		ContentEncoding: delivery.ContentEncoding,
		CorrelationId:   delivery.CorrelationId,
		Body:            []byte(errorMessage),
	}
	return errorChannel.Publish(exchange, routingKey, false, false, publishing)
}

func postMessage(message amqp.Publishing, channel MQChannel, exchange string, routingKey string) error {
	return channel.Publish(exchange, routingKey, false, false, message)
}

func selectElixirIdByEGAId(egaId string) (elixirId string, err error) {
	err = db.QueryRow("select elixir_id from mapping where ega_id = $1", egaId).Scan(&elixirId)
	if err == nil {
		log.Printf("Replacing EGA ID [%s] with Elixir ID [%s]", egaId, elixirId)
	}
	return
}

func selectEgaIdByElixirId(elixirId string) (egaId string, err error) {
	err = db.QueryRow("select ega_id from mapping where elixir_id = $1", elixirId).Scan(&egaId)
	if err == nil {
		log.Printf("Replacing Elixir ID [%s] with EGA ID [%s]", elixirId, egaId)
	}
	return
}

func getTLSConfig() *tls.Config {
	debug := os.Getenv("DEBUG") == "true"
	caCertPath := os.Getenv("CA_CERT_PATH")
	if caCertPath == "" {
		if debug {
			log.Println("CA_CERT_PATH not set, using system cert pool")
		}
		systemPool, err := x509.SystemCertPool()
		if err != nil {
			log.Printf("WARNING: Failed to load system cert pool: %v", err)
			return &tls.Config{InsecureSkipVerify: false}
		}
		if debug {
			log.Println("System cert pool loaded (cannot list subjects due to security).")
		}
		return &tls.Config{
			RootCAs:            systemPool,
			InsecureSkipVerify: false,
		}
	}
	// Load custom CA from path
	if debug {
		log.Printf("Using CA certificate from path: %s", caCertPath)
	}
	caCert, err := os.ReadFile(caCertPath)
	if err != nil {
		log.Fatalf("Failed to read CA certificate: %v", err)
	}
	caCertPool := x509.NewCertPool()
	if !caCertPool.AppendCertsFromPEM(caCert) {
		log.Fatal("Failed to add CA certificate to pool")
	}
	if debug {
		log.Println("Custom CA certificate loaded and added to pool")
	}
	return &tls.Config{
		RootCAs:            caCertPool,
		InsecureSkipVerify: false,
	}
}

func failOnError(err error, msg string) {
	if err != nil {
		log.Fatalf("%s: %s", msg, err)
	}
}
