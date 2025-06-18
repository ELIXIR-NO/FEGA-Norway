package no.elixir.e2eTests;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import no.elixir.e2eTests.constants.Strings;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class AccessionTest extends BaseE2ETest {

    @Test
    public void triggerAccessionMessageFromCEGA() throws Exception {
        setupTestEnvironment();
        try {
            test();
            // Wait for LEGA finalize service to complete and update DB
            waitForProcessing(5000);
        } finally {
            cleanupTestEnvironment();
        }
    }

    private void test() throws Exception {
        log.info("Publishing accession message on behalf of CEGA to CEGA RMQ...");
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
        String randomFileAccessionID = "EGAF5" + getRandomNumber(10);
        String message =
                String.format(
                        Strings.ACCESSION_MESSAGE,
                        env.getCegaAuthUsername(),
                        encFile.getName(),
                        randomFileAccessionID,
                        rawSHA256Checksum,
                        rawMD5Checksum);
        log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
    }

}
