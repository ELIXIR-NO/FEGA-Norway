package no.elixir.fega.ltp.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Data
@Component
@Slf4j
public class RabbitMQProperties {

  private TsdProperties tsd = new TsdProperties();

  @Data
  public static class BaseProperties {

    private String host;
    private int port;
    private String virtualHost;
    private String username;
    private String password;
    private String exchange;
    private String exportRequestsRoutingKey;
    private String inboxRoutingKey;
    private boolean tls;
    private String truststore;
    private String truststorePassword;
  }

  public static class TsdProperties extends BaseProperties {
    // Add TSD-specific properties if needed
  }
}
