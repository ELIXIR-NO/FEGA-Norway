package no.elixir.fega.ltp.config;

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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  private static final String ROLE_ADMIN = "ADMIN";

  @Value("${spring.security.user.name}")
  private String username;

  @Value("${spring.security.user.password}")
  private String password;

  @Bean
  public UserDetailsService userDetailsService() {
    validateCredentials(this.username, this.password); // Validate username and password
    return new InMemoryUserDetailsManager(
        User.withUsername(this.username)
            .password(passwordEncoder().encode(this.password)) // encode password
            .roles(ROLE_ADMIN) // Set role from configuration
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
   *
   * @param http The HttpSecurity instance to configure the security filter chain.
   * @return A SecurityFilterChain instance to enforce the configured security policies.
   * @throws Exception If an error occurs while building the HttpSecurity configuration.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain basicAuthFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/export/**")
        .authorizeHttpRequests(
            auth -> auth.requestMatchers("/export/**").hasRole(ROLE_ADMIN).anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(Customizer.withDefaults()); // Enable basic HTTP authentication
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(); // Encode passwords
  }

  /**
   * Validates the username and password based on given requirements.
   *
   * @throws IllegalArgumentException If the username or password doesn't meet the requirements.
   */
  private void validateCredentials(String username, String password) {
    if (username == null || username.length() < 5) {
      throw new IllegalArgumentException(
          "Username must be at least 5 characters long and not empty.");
    }
    if (password == null || password.length() < 10) {
      throw new IllegalArgumentException(
          "Password must be at least 10 characters long and not empty.");
    }
  }
}
