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

public class AccessionTest {

    public static void publishAccessionMessageOnBehalfOfCEGAToLocalEGA() throws Exception {
        State.log.info("Publishing accession message on behalf of CEGA to CEGA RMQ...");
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
        String randomFileAccessionID = "EGAF5" + CommonUtils.getRandomNumber(10);
        String message =
                String.format(
                        Strings.ACCESSION_MESSAGE,
                        State.env.getCegaAuthUsername(),
                        State.encFile.getName(),
                        randomFileAccessionID,
                        State.rawSHA256Checksum,
                        State.rawMD5Checksum);
        State.log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
    }

}
