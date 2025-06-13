package no.elixir.e2eTests;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import no.elixir.e2eTests.constants.Strings;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class IngestMessageTest extends BaseE2ETest {

    @Test
    public void testTriggerIngestMessage() throws Exception {
        setupTestEnvironment();
        try {
            triggerIngestMessageFromCEGA();
            // Wait for the LEGA ingest and verify services to complete and update DB
            waitForProcessing(5000);
        } finally {
            cleanupTestEnvironment();
        }
    }

    private void triggerIngestMessageFromCEGA() throws Exception {
        log.info("Publishing ingestion message to CentralEGA...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.useSslProtocol(createSslContext());
        factory.setUri(env.getBrokerConnectionString());
        Connection connectionFactory = factory.newConnection();
        Channel channel = connectionFactory.createChannel();
        correlationId = UUID.randomUUID().toString();

        AMQP.BasicProperties properties =
                new AMQP.BasicProperties()
                        .builder()
                        .deliveryMode(2)
                        .contentType("application/json")
                        .contentEncoding(StandardCharsets.UTF_8.displayName())
                        .correlationId(correlationId)
                        .build();

        String message = Strings.INGEST_MESSAGE.formatted(env.getCegaAuthUsername(), encFile.getName());
        log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());

        channel.close();
        connectionFactory.close();
    }

}
