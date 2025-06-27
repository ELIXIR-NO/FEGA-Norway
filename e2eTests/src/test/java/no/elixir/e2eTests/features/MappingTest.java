package no.elixir.e2eTests.features;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.State;
import no.elixir.e2eTests.utils.CertificateUtils;
import no.elixir.e2eTests.utils.CommonUtils;

import java.nio.charset.StandardCharsets;

public class MappingTest {

    /**
     * Trigger the process further, with retrieved
     * information from earlier steps.
     */
    public static void triggerMappingMessageFromCEGA() throws Exception {
        State.log.info("Mapping file to a dataset...");
        State.datasetId = "EGAD" + CommonUtils.getRandomNumber(11);
        ConnectionFactory factory = new ConnectionFactory();
        factory.useSslProtocol(CertificateUtils.createSslContext());
        factory.setUri(State.env.getBrokerConnectionString());
        Connection connectionFactory = factory.newConnection();
        Channel channel = connectionFactory.createChannel();
        AMQP.BasicProperties properties =
                new AMQP.BasicProperties()
                        .builder()
                        .deliveryMode(2)
                        .contentType("application/json")
                        .contentEncoding(StandardCharsets.UTF_8.displayName())
                        .correlationId(State.correlationId)
                        .build();
        String message = String.format(Strings.MAPPING_MESSAGE, State.stableId, State.datasetId);
        State.log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
        State.log.info("Mapping file to dataset ID message sent successfully");
    }

}
