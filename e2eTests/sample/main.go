package main

import (
	"fmt"
	amqp "github.com/rabbitmq/amqp091-go"
)

func main() {
	// AMQP URL for connecting to RabbitMQ
	// Format: amqp://user:password@host:port/vhost
	// Default login for RabbitMQ is usually "guest:guest" on localhost
	amqpURL := "amqp://admin:guest@172.18.0.2:5672/test"
	fmt.Printf("Trying to connect to %s\n", amqpURL)

	// Attempt to connect to RabbitMQ
	conn, err := amqp.Dial(amqpURL)
	if err != nil {
		fmt.Println("Failed to connect to RabbitMQ:", err)
		return
	}
	defer conn.Close() // Ensure the connection is closed when finished
	// If no error, the connection is successful
	fmt.Println("Successfully connected to RabbitMQ!")
}
