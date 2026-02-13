package no.elixir.e2eTests.features;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.CertificateUtils;
import no.elixir.e2eTests.utils.CommonUtils;

public class MappingTest {

  /** Trigger the process further, with retrieved information from earlier steps. */
  public static void triggerMappingMessageFromCEGA() throws Exception {
    E2EState.log.info("Mapping file to a dataset...");
    E2EState.datasetId = "EGAD" + CommonUtils.getRandomNumber(11);
    ConnectionFactory factory = new ConnectionFactory();
    String uri = E2EState.env.getCegaConnString();
    factory.setUri(uri);
    if (uri.startsWith("amqps")) {
      try {
        factory.useSslProtocol(CertificateUtils.createSslContext());
      } catch (Exception e) {
        factory.useSslProtocol(SSLContext.getDefault());
      }
    }
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
