package no.elixir.fega.ltp.config;

import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;

@Slf4j
@Configuration
public class RabbitMQConfig {

  @Bean
  @Primary
  @Qualifier("tsdRabbitTemplate") public RabbitTemplate tsdRabbitTemplate(
      @Qualifier("tsdConnectionFactory") ConnectionFactory connectionFactory,
      MessageConverter messageConverter,
      RabbitMQProperties.TsdProperties tsdProps) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(messageConverter);
    rabbitTemplate.setExchange(tsdProps.getExchange());
    return rabbitTemplate;
  }

  private ConnectionFactory createConnectionFactory(RabbitMQProperties.BaseProperties props)
      throws Exception {
    CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
    connectionFactory.setHost(props.getHost());
    connectionFactory.setPort(props.getPort());
    connectionFactory.setUsername(props.getUsername());
    connectionFactory.setPassword(props.getPassword());
    connectionFactory.setVirtualHost(props.getVirtualHost());

    if (props.isTls()) {
      log.info("Enabling TLS for MQ connection to {}", props.getHost());
      // Get the underlying RabbitMQ factory
      com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory =
          connectionFactory.getRabbitConnectionFactory();

      if (props.getTruststore() != null && !props.getTruststore().isEmpty()) {
        // Configure SSL on the underlying factory
        rabbitConnectionFactory.useSslProtocol(createSslContext(props));
        rabbitConnectionFactory.enableHostnameVerification();
      } else {
        // Use the defaults
        rabbitConnectionFactory.useSslProtocol();
        rabbitConnectionFactory.enableHostnameVerification();
      }
    }

    // Configure connection pooling
    connectionFactory.setChannelCacheSize(10);
    return connectionFactory;
  }

  private SSLContext createSslContext(RabbitMQProperties.BaseProperties props) throws Exception {
    // Load the PKCS12 trust store
    Resource trustStoreResource = new FileUrlResource(props.getTruststore());
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(
        new FileInputStream(trustStoreResource.getFile()),
        props.getTruststorePassword().toCharArray());

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
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  @Qualifier("tsdConnectionFactory") public ConnectionFactory tsdConnectionFactory(RabbitMQProperties.TsdProperties tsdProps)
      throws Exception {
    return createConnectionFactory(tsdProps);
  }

  @Bean
  @ConfigurationProperties(prefix = "mq.tsd")
  public RabbitMQProperties.TsdProperties tsdProperties() {
    return new RabbitMQProperties.TsdProperties();
  }
}
