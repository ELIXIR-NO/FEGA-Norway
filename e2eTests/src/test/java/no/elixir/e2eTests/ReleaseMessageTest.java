package no.elixir.e2eTests;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import no.elixir.e2eTests.constants.Strings;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class ReleaseMessageTest extends BaseE2ETest {

    @Test
    public void testTriggerReleaseMessage() throws Exception {
        setupTestEnvironment();
        try {
            triggerReleaseMessageFromCEGA();
            // Wait for LEGA mapper service to update dataset status
            waitForProcessing(1000);
        } finally {
            cleanupTestEnvironment();
        }
    }

    private void triggerReleaseMessageFromCEGA() throws Exception {
        log.info("Releasing the dataset...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.useSslProtocol(createSslContext());
        factory.setUri(env.getBrokerConnectionString());
        Connection connectionFactory = factory.newConnection();
        Channel channel = connectionFactory.createChannel();
        AMQP.BasicProperties properties =
                new AMQP.BasicProperties()
                        .builder()
                        .deliveryMode(2)
                        .contentType("application/json")
                        .contentEncoding(StandardCharsets.UTF_8.displayName())
                        .correlationId(correlationId)
                        .build();
        String message = String.format(Strings.RELEASE_MESSAGE, datasetId);
        log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
        log.info("Dataset release message sent successfully");
    }

}
