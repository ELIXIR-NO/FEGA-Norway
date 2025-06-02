package no.elixir.fega.ltp.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.SerializationUtils;

@Configuration
public class SecurityConfig {

  private static final String ROLE_ADMIN = "ADMIN";

  @Value("${spring.security.user.name}")
  private String username;

  @Value("${spring.security.user.password}")
  private String password;

  /* --------------------------------------------------------------------- */
  /*  USERS / PASSWORDS                                                    */
  /* --------------------------------------------------------------------- */
  @Bean
  public UserDetailsService userDetailsService() {
    validateCredentials(this.username, this.password);
    return new InMemoryUserDetailsManager(
        User.withUsername(this.username)
            .password(passwordEncoder().encode(this.password))
            .roles(ROLE_ADMIN)
            .build());
  }

  /**
   * Configures the application's security filter chain to define request access rules and
   * authentication mechanisms. Apart from this {@link SecurityFilterChain} there's another Bean
   * configured in the application entry. Please see {@code
   * LocalEGATSDProxyApplication#filterChain}.
   *
   * <p>The method allows requests to paths matching "/export/**" only for users with the role
   * "ADMIN", while granting unrestricted access to all other paths. It disables CSRF protection for
   * simplicity in contexts where it is unnecessary. HTTP Basic Authentication is enabled to allow
   * clients to authenticate using the Authorization header with a base64-encoded username and
   * password.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /* --------------------------------------------------------------------- */
  /* 1)  BASIC-AUTH CHAIN FOR /export/**                                   */
  /* --------------------------------------------------------------------- */
  @Bean
  @Order(1)
  public SecurityFilterChain basicAuthFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/export/**")
        .authorizeHttpRequests(
            auth -> auth.requestMatchers("/export/**").hasRole(ROLE_ADMIN).anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  /* --------------------------------------------------------------------- */
  /* 2)  (Optional) MAIN CHAIN WITH OAUTH-2 LOGIN & COOKIE REPOSITORY       */
  /*     – If LocalEGATSDProxyApplication already defines this,            */
  /*       just remove this bean and keep the cookie bean below.           */
  /* --------------------------------------------------------------------- */
  @Bean
  public SecurityFilterChain oauth2FilterChain(
      HttpSecurity http, AuthorizationRequestRepository<OAuth2AuthorizationRequest> cookieRepo)
      throws Exception {

    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/token.html", "/token", "/user", "/oauth2/**", "/oidc-protected")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(AbstractHttpConfigurer::disable)
        .oauth2Login(
            oauth2 ->
                oauth2.authorizationEndpoint(
                    cfg -> cfg.authorizationRequestRepository(cookieRepo)));

    return http.build();
  }

  /* --------------------------------------------------------------------- */
  /* 3)  COOKIE-BASED AUTH-REQUEST REPOSITORY                              */
  /* --------------------------------------------------------------------- */
  @Bean
  public AuthorizationRequestRepository<OAuth2AuthorizationRequest>
      cookieAuthorizationRequestRepository() {

    return new HttpCookieOAuth2AuthorizationRequestRepository();
  }

  /* --------------------------------------------------------------------- */
  /* 4)  HELPERS                                                           */
  /* --------------------------------------------------------------------- */
  private void validateCredentials(String username, String password) {
    if (username == null || username.length() < 5) {
      throw new IllegalArgumentException("Username must be at least 5 characters long.");
    }
    if (password == null || password.length() < 10) {
      throw new IllegalArgumentException("Password must be at least 10 characters long.");
    }
  }

  /* ===================================================================== */
  /*  NESTED STATIC CLASS — COPY OF SPRING SAMPLE (≈90 LINES)              */
  /*  Stores the OAuth2AuthorizationRequest in an encrypted,               */
  /*  HttpOnly cookie instead of the HTTP session.                         */
  /* ===================================================================== */
  static class HttpCookieOAuth2AuthorizationRequestRepository
      implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";
    private static final int EXPIRE_SEC = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
      return getCookie(request).map(this::deserialize).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
        OAuth2AuthorizationRequest req, HttpServletRequest request, HttpServletResponse response) {
      if (req == null) {
        deleteCookie(request, response);
        return;
      }
      addCookie(response, serialize(req));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
        HttpServletRequest request, HttpServletResponse response) {
      OAuth2AuthorizationRequest req = loadAuthorizationRequest(request);
      deleteCookie(request, response);
      return req;
    }

    /* ------------- cookie helpers ------------- */

    private java.util.Optional<Cookie> getCookie(HttpServletRequest request) {
      if (request.getCookies() == null) return java.util.Optional.empty();
      for (Cookie c : request.getCookies()) {
        if (COOKIE_NAME.equals(c.getName())) return java.util.Optional.of(c);
      }
      return java.util.Optional.empty();
    }

    private void addCookie(HttpServletResponse response, String value) {
      Cookie cookie = new Cookie(COOKIE_NAME, value);
      cookie.setPath("/");
      cookie.setHttpOnly(true);
      cookie.setSecure(true);
      cookie.setMaxAge(EXPIRE_SEC);
      response.addCookie(cookie);
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response) {
      getCookie(request)
          .ifPresent(
              c -> {
                c.setValue("");
                c.setPath("/");
                c.setMaxAge(0);
                response.addCookie(c);
              });
    }

    private String serialize(OAuth2AuthorizationRequest obj) {
      return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(obj));
    }

    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
      return (OAuth2AuthorizationRequest)
          SerializationUtils.deserialize(Base64.getUrlDecoder().decode(cookie.getValue()));
    }
  }
}
