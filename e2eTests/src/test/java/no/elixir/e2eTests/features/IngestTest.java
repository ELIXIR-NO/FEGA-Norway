package no.elixir.e2eTests.features;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.CertificateUtils;

import javax.net.ssl.SSLContext;

public class IngestTest {

  public static void publishIngestionMessageToCEGA() throws Exception {
    E2EState.log.info("Publishing ingestion message to CentralEGA...");
    ConnectionFactory factory = new ConnectionFactory();
      try {
          factory.useSslProtocol(CertificateUtils.createSslContext());
      } catch (Exception e) {
          factory.useSslProtocol(SSLContext.getDefault());
      }
    factory.setUri(E2EState.env.getBrokerConnectionString());
    Connection connectionFactory = factory.newConnection();
    Channel channel = connectionFactory.createChannel();
    E2EState.correlationId = UUID.randomUUID().toString();
    AMQP.BasicProperties properties =
        new AMQP.BasicProperties()
            .builder()
            .deliveryMode(2)
            .contentType("application/json")
            .contentEncoding(StandardCharsets.UTF_8.displayName())
            .correlationId(E2EState.correlationId)
            .build();

    String message =
        Strings.INGEST_MESSAGE.formatted(
            E2EState.env.getCegaAuthUsername(), E2EState.encFile.getName());
    E2EState.log.info(message);
    channel.basicPublish("localega", "files", properties, message.getBytes());
    channel.close();
    connectionFactory.close();
  }
}
