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
func Publish(cfg *config.Config, correlationID string, body []byte) error {
	conn, err := dial(cfg.CegaConnString)
	if err != nil {
		return fmt.Errorf("connecting to CEGA broker: %w", err)
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("opening channel: %w", err)
	}
	defer ch.Close()

	return ch.PublishWithContext(context.Background(), exchange, routingKey, false, false,
		amqp.Publishing{
			DeliveryMode:    amqp.Persistent,
			ContentType:     "application/json",
			ContentEncoding: "UTF-8",
			CorrelationId:   correlationID,
			Body:            body,
		})
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
