// Package amqp publishes the pipeline trigger messages to the CEGA broker. It is
// publish-only: the suite drives the SDA pipeline by emitting ingest, accession,
// mapping and release events, and never consumes from the broker.
package amqp

import (
	"context"
	"crypto/tls"
	"fmt"
	"strings"

	amqp "github.com/rabbitmq/amqp091-go"

	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/certs"
	"github.com/ELIXIR-NO/FEGA-Norway/e2e/internal/config"
)

// Exchange and routing key the CEGA broker publishes to.
const (
	exchange   = "localega"
	routingKey = "files"
)

// Publish sends body to the CEGA broker with persistent delivery,
// application/json, UTF-8 and the given correlation id. For amqps URIs it
// trusts the mkcert rootCA.
//
// Close errors are reported: the broker signals a rejected publish (unknown
// exchange, revoked write permission) by closing the channel asynchronously, so
// discarding them would let a rejected message pass as sent.
func Publish(cfg *config.Config, correlationID string, body []byte) (err error) {
	conn, err := dial(cfg.CegaConnString)
	if err != nil {
		return fmt.Errorf("connecting to CEGA broker: %w", err)
	}
	defer closing(&err, "closing broker connection", conn.Close)

	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("opening channel: %w", err)
	}
	defer closing(&err, "closing channel", ch.Close)

	return ch.PublishWithContext(context.Background(), exchange, routingKey, false, false,
		amqp.Publishing{
			DeliveryMode:    amqp.Persistent,
			ContentType:     "application/json",
			ContentEncoding: "UTF-8",
			CorrelationId:   correlationID,
			Body:            body,
		})
}

// closing runs close and promotes its error into *err, keeping the first
// failure when the publish already failed.
func closing(err *error, context string, close func() error) {
	if cerr := close(); cerr != nil && *err == nil {
		*err = fmt.Errorf("%s: %w", context, cerr)
	}
}

func dial(uri string) (*amqp.Connection, error) {
	if strings.HasPrefix(uri, "amqps") {
		pool, err := certs.LoadRootCAPool()
		if err != nil {
			return nil, fmt.Errorf("loading rootCA for amqps: %w", err)
		}
		return amqp.DialTLS(uri, &tls.Config{RootCAs: pool})
	}
	return amqp.Dial(uri)
}
