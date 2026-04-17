package main

import (
	"database/sql"
	"encoding/json"
	"errors"
	"github.com/ELIXIR-NO/FEGA-Norway/mq-interceptor/validator"
	"io/ioutil"
	"os"
	"reflect"
	"strings"
	"testing"
	"time"

	_ "github.com/proullon/ramsql/driver"
	amqp091 "github.com/rabbitmq/amqp091-go"
	"github.com/spf13/viper"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var settings = map[string]string{
	"POSTGRES_CONNECTION": "postgres://postgres:p0stgres_passw0rd@postgres:5432/postgres?sslmode=disable",
	"LEGA_MQ_CONNECTION":  "amqps://admin:guest@mq:5671/test",
	"LEGA_MQ_EXCHANGE":    "sda",
	"LEGA_MQ_ROUTING_KEY": "files",
	"CEGA_MQ_CONNECTION":  "amqps://test:test@cegamq:5671/lega?cacertfile=/etc/ega/ssl/CA.cert",
	"CEGA_MQ_EXCHANGE":    "localega.v1",
	"CEGA_MQ_QUEUE":       "v1.files",
	"ENABLE_TLS":          "false",
}

func failTestOnError(err error, t *testing.T) {
	if err != nil {
		t.Fatalf("Fatal error: %s", err)
	}
}

func setupDatabase(connection string, t *testing.T) (*sql.DB, error) {
	db_content := []string{
		`CREATE TABLE mapping (ega_id TEXT, elixir_id TEXT);`,
		`INSERT INTO mapping (ega_id, elixir_id) VALUES ('alice@ega.org', 'alice@elixir.org');`,
		`INSERT INTO mapping (ega_id, elixir_id) VALUES ('bob@ega.org', 'bob@elixir.org');`,
		`INSERT INTO mapping (ega_id, elixir_id) VALUES ('carol@ega.org', 'carol@elixir.org');`,
	}

	db, err := sql.Open("ramsql", connection) // The connection string is just ignored
	if err != nil {
		return nil, err
	}
	for _, statement := range db_content {
		t.Logf("SQL: %s\n", statement)
		_, err = db.Exec(statement)
		if err != nil {
			return nil, err
		}
	}
	return db, nil
}

func createDelivery(exchange string, routingkey string, contentType string, message []byte) amqp091.Delivery {
	delivery := amqp091.Delivery{
		Acknowledger:    nil,
		Headers:         amqp091.Table(nil),
		ContentType:     contentType,
		ContentEncoding: "UTF-8",
		DeliveryMode:    0x2,
		Priority:        0x0,
		Timestamp:       time.Now(),
		Type:            routingkey,
		UserId:          "doesnotmatter@whatevs.org",
		Exchange:        exchange,
		RoutingKey:      routingkey,
		Body:            message,
	}
	return delivery
}

func readJSONmap(filename string) (map[string]any, error) {
	jsonFile, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer jsonFile.Close()
	byteValue, _ := ioutil.ReadAll(jsonFile)
	var result map[string]any
	json.Unmarshal([]byte(byteValue), &result)
	return result, nil
}

func readJSONlist(filename string) ([]any, error) {
	jsonFile, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	defer jsonFile.Close()
	byteValue, _ := ioutil.ReadAll(jsonFile)
	var result []any
	json.Unmarshal([]byte(byteValue), &result)
	return result, nil
}

type MQinterceptorTests struct {
	suite.Suite
}

func TestMQinterceptorTests(t *testing.T) {
	suite.Run(t, new(MQinterceptorTests))
}

func (testsuite *MQinterceptorTests) SetupSuite() {
	var err error
	t := testsuite.T()
	viper.Set("log.level", "debug")
	t.Log("\n---------- Environment ----------")
	for k, v := range settings {
		t.Logf("OS.env: %s = %s\n", k, v)
		t.Setenv(k, v)
	}
	t.Log("\n---------- Database ----------")
	db, err = setupDatabase(os.Getenv("POSTGRES_CONNECTION"), t) // "db" is a global variable in "main.go"
	if err != nil {
		t.Fatalf("Error setting up database: %s", err)
	}
	jsonValidator = validator.NewJSONValidator("test/schemas")
	t.Log("\n---------- Run tests ----------")
}

func (testsuite *MQinterceptorTests) TearDownSuite() {
	// t := testsuite.T()
	// t.Log("**** TEAR DOWN Test Suite ****")
	db.Close()
}

func (testsuite *MQinterceptorTests) Test_selectElixirIdByEGAId() {
	t := testsuite.T()
	var elixirID string
	var err error

	elixirID, err = selectElixirIdByEGAId("alice@ega.org")
	t.Logf("Test_selectElixirIdByEGAId #1: 'alice@ega.org' mapped to '%s'", elixirID)
	assert.Equal(t, "alice@elixir.org", elixirID, "EGA ID not mapped to correct Elixir ID")
	assert.Nil(t, err, "EGA<->Elixir ID mapping not found in database")

	elixirID, err = selectElixirIdByEGAId("carol@ega.org")
	t.Logf("Test_selectElixirIdByEGAId #2: 'carol@ega.org' mapped to '%s'", elixirID)
	assert.Equal(t, "carol@elixir.org", elixirID, "EGA ID not mapped to correct Elixir ID")
	assert.Nil(t, err, "EGA<->Elixir ID mapping not found in database")

	elixirID, err = selectElixirIdByEGAId("not_a_real_user") // this should return an empty string and an error
	t.Logf("Test_selectElixirIdByEGAId #3: Non-existing user mapped to '%s'. Error: \"%s\"", elixirID, err)
	assert.Equal(t, "", elixirID, "Database returned non-empty Elixir ID for non-existing user")
	assert.NotNil(t, err, "No error for non-existing Elixir ID when mapping")
}

func (testsuite *MQinterceptorTests) Test_selectEgaIdByElixirId() {
	t := testsuite.T()
	var egaID string
	var err error

	egaID, err = selectEgaIdByElixirId("alice@elixir.org")
	t.Logf("Test_selectEgaIdByElixirId #1: 'alice@elixir.org' mapped to '%s'", egaID)
	assert.Equal(t, "alice@ega.org", egaID, "Elixir ID not mapped to correct EGA ID")
	assert.Nil(t, err, "Elixir<->EGA ID mapping not found in database")

	egaID, err = selectEgaIdByElixirId("carol@elixir.org")
	t.Logf("Test_selectEgaIdByElixirId #2: 'carol@elixir.org' mapped to '%s'", egaID)
	assert.Equal(t, "carol@ega.org", egaID, "Elixir ID not mapped to correct EGA ID")
	assert.Nil(t, err, "Elixir<->EGA ID mapping not found in database")

	egaID, err = selectEgaIdByElixirId("not_a_real_user") // this should return an empty string and an error
	t.Logf("Test_selectEgaIdByElixirId #3: Non-existing user mapped to '%s'. Error: \"%s\"", egaID, err)
	assert.Equal(t, "", egaID, "Database returned non-empty EGA ID for non-existing user")
	assert.NotNil(t, err, "No error for non-existing EGA ID when mapping")
}

func (testsuite *MQinterceptorTests) Test_publishError() {
	t := testsuite.T()
	error_message := "something went terribly wrong"
	original_error := errors.New(error_message)
	original_message := "This is a mock delivery, destined to fail"
	delivery := amqp091.Delivery{
		Acknowledger:    nil,
		Headers:         amqp091.Table(nil),
		ContentType:     "application/json",
		ContentEncoding: "UTF-8",
		DeliveryMode:    0x2,
		Priority:        0x0,
		Timestamp:       time.Now(),
		MessageCount:    0x0,
		DeliveryTag:     0x4,
		Redelivered:     false,
		Exchange:        "localega.v1",
		RoutingKey:      "files",
		Body:            []byte(original_message),
	}
	cegaConfig, err := readJSONmap("test/cegamq.json")
	failTestOnError(err, t)
	var errorPublishChannel MQChannel = CreateMockChannel(cegaConfig)
	err = publishError(delivery, original_error, errorPublishChannel, os.Getenv("CEGA_MQ_EXCHANGE"), "files.error")
	assert.Nil(t, err, "publishError (to CEGA) returned an unexpected error")
	msg := errorPublishChannel.(*MockChannel).GetMessage("v1.files.error")

	t.Logf("Test_publishError #1: Checking that error message was posted correctly")
	assert.NotNil(t, msg, "Error message not posted correctly to CEGA")

	t.Logf("Test_publishError #2: Checking contents of error post")
	var err_msg map[string]string
	json.Unmarshal([]byte(msg.Body), &err_msg)
	assert.Equal(t, error_message, err_msg["reason"], "The 'reason' field of the error post was not as expected")
	assert.Equal(t, original_message, err_msg["original_message"], "The 'original_message' field of the error post was not as expected")
}

func (testsuite *MQinterceptorTests) Test_postMessage() {
	t := testsuite.T()
	message := "{\"id\" : \"xyz\", \"msg\" : \"postMessage test\"}"
	publishing := amqp091.Publishing{
		ContentType:     "application/json",
		ContentEncoding: "UTF-8",
		Body:            []byte(message),
	}
	legaConfig, err := readJSONmap("test/legamq.json")
	failTestOnError(err, t)
	var publishChannel MQChannel = CreateMockChannel(legaConfig)
	err = postMessage(publishing, publishChannel, os.Getenv("LEGA_MQ_EXCHANGE"), "ingest")
	assert.Nil(t, err, "postMessage (to LEGA) returned an unexpected error")
	msg := publishChannel.(*MockChannel).GetMessage("ingest")

	t.Logf("Test_postMessage #1: Checking that message was posted correctly")
	assert.NotNil(t, msg, "Message not posted correctly to LEGA ingest queue")

	t.Logf("Test_postMessage #2: Checking contents of posted message")
	assert.Equal(t, string(message), string(msg.Body), "Retrieved message was different from original posted message")

	// according to the LEGA configuration loaded above, a message with an uknown routing key will be reposted
	// to the alternate exchange (of type "fanout") and end up in the "dropped_messages" queue bound to it
	err = postMessage(publishing, publishChannel, os.Getenv("LEGA_MQ_EXCHANGE"), "unknown")
	assert.Nil(t, err, "postMessage (to LEGA) returned an unexpected error")
	msgDropped := publishChannel.(*MockChannel).GetMessage("dropped_messages")
	t.Logf("Test_postMessage #3: Checking message posted to LEGA with unrecognized routing key")
	assert.NotNil(t, msgDropped, "Message with unknown routing key posted to LEGA did not end up in 'dropped_messages' queue")
}

func (testsuite *MQinterceptorTests) Test_buildPublishingFromDelivery() {
	t := testsuite.T()
	cegaExchange := os.Getenv("CEGA_MQ_EXCHANGE")
	legaExchange := os.Getenv("LEGA_MQ_EXCHANGE")
	deliveryTests, err := readJSONlist("test/tests.json")
	failTestOnError(err, t)
	if len(deliveryTests) == 0 {
		failTestOnError(errors.New("No tests read from 'test/tests.json'"), t)
	}

	for index, element := range deliveryTests {
		deliveryTest := element.(map[string]any)
		direction := deliveryTest["direction"].(string)
		testname := deliveryTest["testname"].(string)
		shouldFail := deliveryTest["fails"].(bool)
		contentType, ok := deliveryTest["contentType"].(string)
		if !ok {
			contentType = "application/json"
		}
		newuser, _ := deliveryTest["enduser"].(string)
		olduser := ""
		expectedMessageType := ""
		var message []byte
		var delivery amqp091.Delivery
		expectedMessage := ""
		msgBody := deliveryTest["message"]
		if msgBodyString, ok := msgBody.(string); ok {
			message = []byte(msgBodyString)
		} else if msgBodyMap, ok := msgBody.(map[string]any); ok {
			message, err = json.Marshal(msgBody.(map[string]any))
			failTestOnError(err, t)
			olduser, _ = msgBodyMap["user"].(string)
			expectedMessageType, _ = msgBodyMap["type"].(string)
		} else {
			failTestOnError(errors.New("The 'message' field must either be a quoted string or JSON map in 'test/tests.json'"), t)
		}
		if olduser != "" && newuser != "" && olduser != newuser {
			expectedMessage = strings.Replace(string(message), olduser, newuser, -1)
		} else {
			expectedMessage = string(message)
		}
		var dirString string
		if direction == "lega" {
			dirString = "from CEGA to LEGA"
			delivery = createDelivery(legaExchange, "", contentType, message)
		} else if direction == "cega" {
			dirString = "from LEGA to CEGA"
			delivery = createDelivery(cegaExchange, deliveryTest["routingkey"].(string), contentType, message)
		} else {
			assert.True(t, (direction == "lega" || direction == "cega"), "TEST DECLARATION ERROR: 'direction' attribute must be either 'lega' or 'cega'")
		}
		publishing, messageType, err := buildPublishingFromDelivery(direction == "lega", delivery)

		t.Logf("Test_buildPublishingFromDelivery #%d: \"%s\"", index+1, testname)

		if shouldFail {
			assert.NotNil(t, err, "buildPublishingFromDelivery did not return an error upon failure")
		} else {
			assert.NotNil(t, publishing, "buildPublishingFromDelivery did not return a publishing object")
			assert.Nilf(t, err, "buildPublishingFromDelivery returned an unexpected error: %s", err)
			if publishing != nil {
				resultMessage := string(publishing.Body)
				assert.Equalf(t, expectedMessage, resultMessage, "Content of message forwarded %s was not as expected", dirString)
				comparePublishingFieldsAgainstDelivery(*publishing, delivery, testsuite)
			}
			if expectedMessageType != "" {
				assert.Equal(t, expectedMessageType, messageType, "buildPublishingFromDelivery returned unexpected messageType")
			}
		}
	}
}

func (testsuite *MQinterceptorTests) Test_forwardDeliveryTo() {
	t := testsuite.T()
	cegaConfig, err := readJSONmap("test/cegamq.json")
	failTestOnError(err, t)
	legaConfig, err := readJSONmap("test/legamq.json")
	failTestOnError(err, t)
	cegaExchange := os.Getenv("CEGA_MQ_EXCHANGE")
	legaExchange := os.Getenv("LEGA_MQ_EXCHANGE")
	var cegaPublishChannel MQChannel = CreateMockChannel(cegaConfig)
	var cegaConsumeChannel MQChannel = CreateMockChannel(cegaConfig)
	var legaPublishChannel MQChannel = CreateMockChannel(legaConfig)
	var legaConsumeChannel MQChannel = CreateMockChannel(legaConfig)

	var bridge *Bridge = &Bridge{
		CEGAConsumeChannel: cegaConsumeChannel,
		CEGAPublishChannel: cegaPublishChannel,
		CEGAErrorChannel:   cegaPublishChannel,
		CEGAExchange:       cegaExchange,
		LEGAConsumeChannel: legaConsumeChannel,
		LEGAPublishChannel: legaPublishChannel,
		LEGAErrorChannel:   legaPublishChannel,
		LEGAExchange:       legaExchange,
	}

	forwardDeliveryTests, err := readJSONlist("test/tests.json")
	failTestOnError(err, t)
	if len(forwardDeliveryTests) == 0 {
		failTestOnError(errors.New("No tests read from 'test/tests.json'"), t)
	}
	for index, element := range forwardDeliveryTests {
		forwardTest := element.(map[string]any)
		direction := forwardTest["direction"].(string)
		testname := forwardTest["testname"].(string)
		shouldFail := forwardTest["fails"].(bool)
		queue := forwardTest["queue"].(string)
        contentType, ok := forwardTest["contentType"].(string)
        if !ok {
            contentType = "application/json"
        }
		newuser, _ := forwardTest["enduser"].(string)
		olduser := ""
		cause, _ := forwardTest["cause"].(string)
		var message []byte
		expectedMessage := ""
		msgBody := forwardTest["message"]
		if msgBodyString, ok := msgBody.(string); ok {
			message = []byte(msgBodyString)
		} else if msgBodyMap, ok := msgBody.(map[string]any); ok {
			message, err = json.Marshal(msgBody.(map[string]any))
			failTestOnError(err, t)
			olduser, _ = msgBodyMap["user"].(string)
		} else {
			failTestOnError(errors.New("The 'message' field must either be a quoted string or JSON map in 'test/tests.json'"), t)
		}
		if olduser != "" && newuser != "" && olduser != newuser {
			expectedMessage = strings.Replace(string(message), olduser, newuser, -1)
		} else {
			expectedMessage = string(message)
		}

		var ack, nack bool
		var forwardedMessage *amqp091.Publishing
		var cegaErrorMessage *amqp091.Publishing
		var legaErrorMessage *amqp091.Publishing
		var dirString string

		if direction == "lega" {
			t.Logf("Test_forwardDeliveryTo #%d: \"%s\"  (CEGA to LEGA)", index+1, testname)
			dirString = "from CEGA to LEGA"
			routingKey := ""
			delivery := createDelivery(legaExchange, routingKey, contentType, message)
			forwardDeliveryTo(true, bridge, routingKey, delivery)
			forwardedMessage = legaPublishChannel.(*MockChannel).GetMessage(queue)
			ack, nack = cegaConsumeChannel.(*MockChannel).GetAckNack()
		} else if direction == "cega" {
			t.Logf("Test_forwardDeliveryTo #%d: \"%s\"  (LEGA to CEGA)", index+1, testname)
			dirString = "from LEGA to CEGA"
			routingKey := forwardTest["routingkey"].(string)
			delivery := createDelivery(cegaExchange, routingKey, contentType, message)
			forwardDeliveryTo(false, bridge, routingKey, delivery)
			forwardedMessage = cegaPublishChannel.(*MockChannel).GetMessage(queue)
			ack, nack = legaConsumeChannel.(*MockChannel).GetAckNack()
		} else {
			assert.True(t, (direction == "lega" || direction == "cega"), "TEST DECLARATION ERROR: 'direction' attribute must be either 'lega' or 'cega'")
		}

		cegaErrorMessage = bridge.CEGAErrorChannel.(*MockChannel).GetMessage("v1.files.error")
		legaErrorMessage = bridge.LEGAErrorChannel.(*MockChannel).GetMessage("dropped_messages")

		if shouldFail {
			assert.Nilf(t, forwardedMessage, "A failed delivery was incorrectly posted to queue %s", queue)
			if strings.HasPrefix(cause, "validation") {
				assert.Truef(t, ack, "Failed delivery %s (due to JSON validation error) was not ACK'ed properly", dirString)
				assert.Falsef(t, nack, "Failed delivery %s (due to JSON validation error) was uncorrectly NACK'ed", dirString)
				assert.NotNilf(t, legaErrorMessage, "Validation error did not result in message ending up in LEGA queue 'dropped_messages'")
				assert.Equalf(t, message, legaErrorMessage.Body, "Dropped message does not equal original message")
				assert.Nilf(t, cegaErrorMessage, "Validation error incorrectly resulted in error message being posted to CEGA")
			} else {
				assert.Truef(t, nack, "Failed delivery %s was not NACK'ed properly", dirString)
				assert.Falsef(t, ack, "Failed delivery %s was uncorrectly ACK'ed", dirString)
				assert.NotNilf(t, cegaErrorMessage, "Failed delivery did not result in error post to CEGA")
				assert.NotNilf(t, legaErrorMessage, "Failed delivery did not result in error post to LEGA ending up in the queue 'dropped_messages'")
			}
		} else {
			assert.NotNilf(t, forwardedMessage, "Message forwarded %s was not found in the correct queue", dirString)
			if forwardedMessage != nil {
				resultMessage := string(forwardedMessage.Body)
				assert.Equalf(t, expectedMessage, resultMessage, "Content of message forwarded %s was not as expected", dirString)
			}
			assert.Truef(t, ack, "Message %s was not ACK'ed properly", dirString)
			assert.Falsef(t, nack, "Message %s was incorrectly NACK'ed", dirString)
			assert.Nilf(t, cegaErrorMessage, "Error message posted incorrectly to CEGA when forwarding message %s", dirString)
			assert.Nilf(t, legaErrorMessage, "Error message posted incorrectly to LEGA when forwarding message %s", dirString)
		}
		bridge.CEGAConsumeChannel.(*MockChannel).Clear()
		bridge.CEGAPublishChannel.(*MockChannel).Clear()
		bridge.CEGAErrorChannel.(*MockChannel).Clear()
		bridge.LEGAConsumeChannel.(*MockChannel).Clear()
		bridge.LEGAPublishChannel.(*MockChannel).Clear()
		bridge.LEGAErrorChannel.(*MockChannel).Clear()
	}
}

func comparePublishingFieldsAgainstDelivery(publishing any, delivery any, testsuite *MQinterceptorTests) {
	t := testsuite.T()
	valueTypePublishing := reflect.ValueOf(publishing)
	valueTypeDelivery := reflect.ValueOf(delivery)
	valueTypeTypePublishing := valueTypePublishing.Type()

	for i := 0; i < valueTypePublishing.NumField(); i++ {
		fieldName := valueTypeTypePublishing.Field(i).Name
		fieldValuePublishing := valueTypePublishing.FieldByName(fieldName).Interface()
		fieldValueDelivery := valueTypeDelivery.FieldByName(fieldName).Interface()
		typeVP := reflect.TypeOf(fieldValuePublishing)
		typeVD := reflect.TypeOf(fieldValueDelivery)
		if typeVP.Comparable() && typeVD.Comparable() {
			assert.Equalf(t, fieldValueDelivery, fieldValuePublishing, "Field '%s' in Publishing not set to same value as in original Delivery", fieldName)
		}
	}
}
