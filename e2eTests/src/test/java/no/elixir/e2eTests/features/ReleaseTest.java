package no.elixir.e2eTests.features;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.CertificateUtils;

import java.nio.charset.StandardCharsets;

public class ReleaseTest {

    public static void triggerReleaseMessageFromCEGA() throws Exception {
        E2EState.log.info("Releasing the dataset...");
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
        String message = String.format(Strings.RELEASE_MESSAGE, E2EState.datasetId);
        E2EState.log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
        E2EState.log.info("Dataset release message sent successfully");
    }

}
