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
import java.util.UUID;

public class IngestTest {

    public static void publishIngestionMessageToCEGA() throws Exception {
        State.log.info("Publishing ingestion message to CentralEGA...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.useSslProtocol(CertificateUtils.createSslContext());
        factory.setUri(State.env.getBrokerConnectionString());
        Connection connectionFactory = factory.newConnection();
        Channel channel = connectionFactory.createChannel();
        State.correlationId = UUID.randomUUID().toString();
        AMQP.BasicProperties properties =
                new AMQP.BasicProperties()
                        .builder()
                        .deliveryMode(2)
                        .contentType("application/json")
                        .contentEncoding(StandardCharsets.UTF_8.displayName())
                        .correlationId(State.correlationId)
                        .build();

        String message = Strings.INGEST_MESSAGE.formatted(State.env.getCegaAuthUsername(), State.encFile.getName());
        State.log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
    }

}
