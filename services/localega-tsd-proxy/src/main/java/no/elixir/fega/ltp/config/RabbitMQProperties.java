package no.elixir.fega.ltp.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mq")
@Slf4j
public class RabbitMQProperties {

  private CegaProperties cega = new CegaProperties();
  private TsdProperties tsd = new TsdProperties();

  @Data
  public static class BaseProperties {
    private String host;
    private int port;
    private String virtualHost;
    private String username;
    private String password;
    private String exchange;
    private String routingKey;
    private boolean tls;
    private String truststore;
    private String truststorePassword;
  }

  public static class CegaProperties extends BaseProperties {
    // Add CEGA-specific properties if needed
  }

  public static class TsdProperties extends BaseProperties {
    // Add TSD-specific properties if needed
  }
}
