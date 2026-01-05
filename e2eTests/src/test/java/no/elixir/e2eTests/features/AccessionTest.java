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

public class AccessionTest {

  public static void publishAccessionMessageOnBehalfOfCEGAToLocalEGA() throws Exception {
    E2EState.log.info("Publishing accession message on behalf of CEGA to CEGA RMQ...");
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
    String randomFileAccessionID = "EGAF5" + CommonUtils.getRandomNumber(10);
    E2EState.stableId = randomFileAccessionID; // shortcut. see: FinalizeTest
    String message =
        String.format(
            Strings.ACCESSION_MESSAGE,
            E2EState.env.getCegaAuthUsername(),
            E2EState.env.getTsdProject(),
            E2EState.env.getLsaaiSubject(),
            E2EState.encFile.getName(),
            randomFileAccessionID,
            E2EState.rawSHA256Checksum,
            E2EState.rawMD5Checksum);
    E2EState.log.info(message);
    channel.basicPublish("localega", "files", properties, message.getBytes());
    channel.close();
    connectionFactory.close();
  }
}
