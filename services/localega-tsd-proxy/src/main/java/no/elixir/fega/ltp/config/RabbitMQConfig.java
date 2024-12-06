package no.elixir.fega.ltp.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  @Bean
  @ConfigurationProperties(prefix = "spring.rabbitmq.connections.cega")
  public CachingConnectionFactory cegaConnectionFactory() {
    return new CachingConnectionFactory();
  }

  @Bean
  public RabbitTemplate cegaRabbitTemplate(CachingConnectionFactory cegaConnectionFactory) {
    return new RabbitTemplate(cegaConnectionFactory);
  }

  @Bean
  @ConfigurationProperties(prefix = "spring.rabbitmq.connections.tsd")
  public CachingConnectionFactory tsdConnectionFactory() {
    return new CachingConnectionFactory();
  }

  @Bean
  public RabbitTemplate tsdRabbitTemplate(CachingConnectionFactory tsdConnectionFactory) {
    return new RabbitTemplate(tsdConnectionFactory);
  }
}
