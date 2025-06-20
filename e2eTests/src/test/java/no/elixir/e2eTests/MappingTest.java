package no.elixir.e2eTests;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import no.elixir.e2eTests.constants.Strings;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class MappingTest extends BaseE2ETest {

    @Test
    public void triggerMappingMessageFromCEGA() throws Exception {
        setupTestEnvironment();
        try {
            // Trigger the process further,
            // with retrieved information from earlier steps
            test();
            // Wait for LEGA mapper service to store mapping
            waitForProcessing(1000);
        } finally {
            cleanupTestEnvironment();
        }
    }

    private void test() throws Exception {
        log.info("Mapping file to a dataset...");
        datasetId = "EGAD" + getRandomNumber(11);
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
        String message = String.format(Strings.MAPPING_MESSAGE, stableId, datasetId);
        log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
        log.info("Mapping file to dataset ID message sent successfully");
    }

}
