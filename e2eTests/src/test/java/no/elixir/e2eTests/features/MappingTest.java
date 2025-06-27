package no.elixir.e2eTests.features;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.CertificateUtils;
import no.elixir.e2eTests.utils.CommonUtils;

import java.nio.charset.StandardCharsets;

public class MappingTest {

    /**
     * Trigger the process further, with retrieved
     * information from earlier steps.
     */
    public static void triggerMappingMessageFromCEGA() throws Exception {
        E2EState.log.info("Mapping file to a dataset...");
        E2EState.datasetId = "EGAD" + CommonUtils.getRandomNumber(11);
        ConnectionFactory factory = new ConnectionFactory();
        factory.useSslProtocol(CertificateUtils.createSslContext());
        factory.setUri(E2EState.env.getBrokerConnectionString());
        Connection connectionFactory = factory.newConnection();
        Channel channel = connectionFactory.createChannel();
        AMQP.BasicProperties properties =
                new AMQP.BasicProperties()
                        .builder()
                        .deliveryMode(2)
                        .contentType("application/json")
                        .contentEncoding(StandardCharsets.UTF_8.displayName())
                        .correlationId(E2EState.correlationId)
                        .build();
        String message = String.format(Strings.MAPPING_MESSAGE, E2EState.stableId, E2EState.datasetId);
        E2EState.log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
        E2EState.log.info("Mapping file to dataset ID message sent successfully");
    }

}
