package no.elixir.fega.ltp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.tc.TSDFileAPIClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.PortMapperImpl;
import org.springframework.security.web.PortResolverImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Spring Boot main file containing the application entry-point and all necessary Spring beans
 * configuration.
 */
@Slf4j
@EnableCaching
@SpringBootApplication
@EnableWebSecurity
public class LocalEGATSDProxyApplication {

  @Value("${token.redirect-uri:{baseUrl}/oidc-protected}")
  private String redirectUri;

  public static void main(String[] args) {
    SpringApplication.run(LocalEGATSDProxyApplication.class, args);
  }

  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    PortMapperImpl portMapper = new PortMapperImpl();
    portMapper.setPortMappings(Collections.singletonMap("8080", "8080"));
    PortResolverImpl portResolver = new PortResolverImpl();
    portResolver.setPortMapper(portMapper);
    LoginUrlAuthenticationEntryPoint entryPoint =
        new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/elixir-aai");
    entryPoint.setPortMapper(portMapper);
    entryPoint.setPortResolver(portResolver);
    http.requiresChannel(channel -> channel.anyRequest().requiresSecure())
        .exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
        .csrf(AbstractHttpConfigurer::disable)
        .securityMatcher(
            "/token.html", "/token", "/user", "/oauth2/authorization/elixir-aai", "/oidc-protected")
        .authorizeHttpRequests(
            request ->
                request
                    .requestMatchers("/token.html")
                    .authenticated()
                    .requestMatchers("/token")
                    .authenticated()
                    .requestMatchers("/user")
                    .authenticated())
        .oauth2Login(
            auth ->
                auth.redirectionEndpoint(endpoint -> endpoint.baseUri("/oidc-protected"))
                    .defaultSuccessUrl("/"));

    return http.build();
  }

  @Bean
  public ClientRegistrationRepository clientRegistrationRepository(
      @Value("${elixir.client.id}") String elixirAAIClientId,
      @Value("${elixir.client.secret}") String elixirAAIClientSecret) {
    return new InMemoryClientRegistrationRepository(
        ClientRegistration.withRegistrationId("elixir-aai")
            .clientId(elixirAAIClientId)
            .clientSecret(elixirAAIClientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirectUri)
            .scope("openid", "ga4gh_passport_v1")
            .authorizationUri("https://login.elixir-czech.org/oidc/authorize")
            .tokenUri("https://login.elixir-czech.org/oidc/token")
            .userInfoUri("https://login.elixir-czech.org/oidc/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .jwkSetUri("https://login.elixir-czech.org/oidc/jwk")
            .clientName("elixir-aai")
            .build());
  }

  @Primary
  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .connectTimeout(Duration.ofMillis(5000))
        .readTimeout(Duration.ofMillis(5000))
        .build();
  }

  @Bean
  public TSDFileAPIClient tsdFileAPIClient(
      @Value("${tsd.secure}") String secure,
      @Value("${tsd.host}") String tsdHost,
      @Value("${tsd.project}") String tsdProject,
      @Value("${tsd.access-key}") String tsdAccessKey,
      @Value("${tsd.root-ca}") String tsdRootCA,
      @Value("${tsd.root-ca-password}") String tsdRootCAPassword)
      throws GeneralSecurityException, IOException {
    TSDFileAPIClient.Builder tsdFileAPIClientBuilder =
        new TSDFileAPIClient.Builder()
            .secure(secure)
            .host(tsdHost)
            .project(tsdProject)
            .accessKey(tsdAccessKey);
    if (StringUtils.hasLength(tsdRootCA) && StringUtils.hasLength(tsdRootCAPassword)) {
      X509TrustManager trustManager =
          trustManagerForCertificates(Files.newInputStream(Path.of(tsdRootCA)), tsdRootCAPassword);
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {trustManager}, null);
      OkHttpClient httpClient =
          new OkHttpClient.Builder()
              .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
              .build();
      // CloseableHttpClient httpClient =
      // HttpClients.custom().setSSLContext(sslContext).build();
      log.info("TSD File API Client initialized with custom HTTP client, root CA: {}", tsdRootCA);
      return tsdFileAPIClientBuilder.httpClient(httpClient).build();
    } else {
      log.info("TSD File API Client initialized");
    }
    return tsdFileAPIClientBuilder.build();
  }

  private X509TrustManager trustManagerForCertificates(InputStream in, String password)
      throws GeneralSecurityException, IOException {
    Collection<Certificate> certificates = readCertificates(in, password);
    if (certificates.isEmpty()) {
      throw new IllegalArgumentException("Expected non-empty set of trusted certificates");
    }

    // put the certificates into a key store
    char[] pass = UUID.randomUUID().toString().toCharArray(); // any password will do
    KeyStore keyStore = newEmptyKeyStore(pass);
    for (Certificate certificate : certificates) {
      keyStore.setCertificateEntry(UUID.randomUUID().toString(), certificate);
    }

    // use it to build an X509 trust manager
    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, pass);
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(keyStore);
    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
    if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
      throw new IllegalStateException(
          "Unexpected default trust managers: " + Arrays.toString(trustManagers));
    }
    return (X509TrustManager) trustManagers[0];
  }

  private Collection<Certificate> readCertificates(InputStream in, String password)
      throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
    KeyStore p12 = KeyStore.getInstance("pkcs12");
    p12.load(in, password.toCharArray());
    Enumeration<String> e = p12.aliases();
    Collection<Certificate> result = new ArrayList<>();
    while (e.hasMoreElements()) {
      result.add(p12.getCertificate(e.nextElement()));
    }
    return result;
  }

  private KeyStore newEmptyKeyStore(char[] password) throws GeneralSecurityException {
    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, password);
      return keyStore;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
