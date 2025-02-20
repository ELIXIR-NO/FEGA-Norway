package no.elixir.fega.ltp.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
  //
  //  @Bean
  //  @ConfigurationProperties(prefix = "spring.rabbitmq.connections.cega")
  //  public CachingConnectionFactory cegaConnectionFactory() {
  //    return new CachingConnectionFactory();
  //  }
  //
  ////  @Bean
  ////  public CachingConnectionFactory cegaConnectionFactory(
  ////          @Value("${spring.rabbitmq.connections.cega.host}") String host,
  ////          @Value("${spring.rabbitmq.connections.cega.port}") int port,
  ////          @Value("${spring.rabbitmq.connections.cega.username}") String username,
  ////          @Value("${spring.rabbitmq.connections.cega.password}") String password,
  ////          @Value("${spring.rabbitmq.connections.cega.virtual-host}") String virtualHost,
  ////          @Value("${spring.rabbitmq.connections.cega.ssl.validate-server-certificate}")
  ////          boolean validateServerCertificate,
  ////          @Value("${spring.rabbitmq.connections.cega.ssl.enabled}") boolean sslEnabled,
  ////          @Value("${spring.rabbitmq.connections.cega.ssl.algorithm}") String sslAlgorithm,
  ////          @Value("${spring.rabbitmq.connections.cega.ssl.trust-store}") String trustStorePath,
  ////          @Value("${spring.rabbitmq.connections.cega.ssl.trust-store-password}")
  ////          String trustStorePassword)
  ////          throws Exception {
  ////    var x = new CachingConnectionFactory();
  ////    x.getRabbitConnectionFactory().setHost(host);
  ////    x.getRabbitConnectionFactory().setPort(port);
  ////    x.getRabbitConnectionFactory().setUsername(username);
  ////    x.getRabbitConnectionFactory().setPassword(password);
  ////    x.getRabbitConnectionFactory().useSslProtocol();
  ////    x.getRabbitConnectionFactory().setVirtualHost(virtualHost);
  ////    if (sslEnabled) {
  ////      SSLContext sslContext =
  ////              createSSLContext(
  ////                      trustStorePath, trustStorePassword, sslAlgorithm,
  // validateServerCertificate);
  ////      x.getRabbitConnectionFactory().useSslProtocol(sslContext);
  ////    }
  ////    return x;
  ////  }
  //
  ////  private SSLContext createSSLContext(
  ////      String trustStorePath,
  ////      String trustStorePassword,
  ////      String sslAlgorithm,
  ////      boolean validateServerCertificate)
  ////      throws Exception {
  ////    KeyStore trustStore = KeyStore.getInstance("JKS");
  ////    try (FileInputStream trustStoreStream = new FileInputStream(trustStorePath)) {
  ////      trustStore.load(trustStoreStream, trustStorePassword.toCharArray());
  ////    }
  ////
  ////    TrustManagerFactory trustManagerFactory =
  ////        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
  ////    trustManagerFactory.init(trustStore);
  ////
  ////    SSLContext sslContext = SSLContext.getInstance(sslAlgorithm);
  ////
  ////    // If certificate validation is disabled, use a permissive TrustManager
  ////    if (!validateServerCertificate) {
  ////      TrustManager[] trustManagers =
  ////          new TrustManager[] {
  ////            new X509TrustManager() {
  ////              public void checkClientTrusted(X509Certificate[] chain, String authType) {}
  ////
  ////              public void checkServerTrusted(X509Certificate[] chain, String authType) {}
  ////
  ////              public X509Certificate[] getAcceptedIssuers() {
  ////                return new X509Certificate[0];
  ////              }
  ////            }
  ////          };
  ////      sslContext.init(null, trustManagers, new SecureRandom());
  ////    } else {
  ////      sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
  ////    }
  ////
  ////    return sslContext;
  ////  }
  //
  //
  //  @Bean
  //  public RabbitTemplate cegaRabbitTemplate(CachingConnectionFactory cegaConnectionFactory) {
  //    return new RabbitTemplate(cegaConnectionFactory);
  //  }
  //
  //  @Bean
  //  @ConfigurationProperties(prefix = "spring.rabbitmq.connections.tsd")
  //  public CachingConnectionFactory tsdConnectionFactory() {
  //    return new CachingConnectionFactory();
  //  }
  //
  //  @Bean
  //  public RabbitTemplate tsdRabbitTemplate(CachingConnectionFactory tsdConnectionFactory) {
  //    return new RabbitTemplate(tsdConnectionFactory);
  //  }
}
