package no.elixir.fega.ltp.config;

import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;

@Slf4j
@Configuration
public class CegaMQRabbitConfig {

  @Value("${mq.cega.host}")
  private String host;

  @Value("${mq.cega.port}")
  private int port;

  @Value("${mq.cega.virtual-host}")
  private String virtualHost;

  @Value("${mq.cega.username}")
  private String username;

  @Value("${mq.cega.password}")
  private String password;

  @Value("${mq.cega.tls}")
  private boolean cegaTls;

  @Value("${mq.cega.truststore}")
  private String trustStoreLocation;

  @Value("${mq.cega.truststore-password}")
  private String trustStorePassword;

  @Bean
  public ConnectionFactory connectionFactory() throws Exception {
    CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
    connectionFactory.setHost(host);
    connectionFactory.setPort(port);
    connectionFactory.setUsername(username);
    connectionFactory.setPassword(password);
    connectionFactory.setVirtualHost(virtualHost);
    if (cegaTls) {
      log.info("Enabling TLS for CEGA MQ");
      // Get the underlying RabbitMQ factory
      com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory =
              connectionFactory.getRabbitConnectionFactory();
      if (Strings.isNotBlank(trustStoreLocation)) {
        // Configure SSL on the underlying factory
        rabbitConnectionFactory.useSslProtocol(createSslContext());
      } else {
        // Use the defaults
        rabbitConnectionFactory.useSslProtocol();
      }
    }
    // Optional: Configure connection pooling
    connectionFactory.setChannelCacheSize(10);
    connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);

    return connectionFactory;
  }

  private SSLContext createSslContext() throws Exception {
    // Load the PKCS12 trust store
    Resource trustStoreResource = new FileUrlResource(trustStoreLocation);
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(
        new FileInputStream(trustStoreResource.getFile()), trustStorePassword.toCharArray());

    // Create trust manager
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);

    // Create and initialize the SSLContext
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

    return sslContext;
  }

  @Bean
  public RabbitTemplate cegaMqRabbitTemplate(
      ConnectionFactory connectionFactory, MessageConverter messageConverter) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(messageConverter);
    return rabbitTemplate;
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  //    @Bean
  //    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
  //        return new RabbitAdmin(connectionFactory);
  //    }

  //    // Define your queues, exchanges, and bindings
  //    @Bean
  //    public org.springframework.amqp.core.Queue myQueue() {
  //        return new org.springframework.amqp.core.Queue("my.queue", true);
  //    }
  //
  //    @Bean
  //    public org.springframework.amqp.core.DirectExchange myExchange() {
  //        return new org.springframework.amqp.core.DirectExchange("my.exchange");
  //    }
  //
  //    @Bean
  //    public org.springframework.amqp.core.Binding binding() {
  //        return org.springframework.amqp.core.BindingBuilder
  //                .bind(myQueue())
  //                .to(myExchange())
  //                .with("my.routing.key");
  //    }
}
