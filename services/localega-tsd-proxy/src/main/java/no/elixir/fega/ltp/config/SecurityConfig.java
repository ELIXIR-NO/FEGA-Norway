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
    validateCredentials(this.username, this.password);
    return new InMemoryUserDetailsManager(
        User.withUsername(this.username)
            .password(passwordEncoder().encode(this.password))
            .roles(ROLE_ADMIN)
            .build());
  }

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

  @Bean
  public SecurityFilterChain publicAccessFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/static/**",
                        "/token.html",
                        "/token",
                        "/user",
                        "/oauth2/**",
                        "/oidc-protected",
                        "/submission.html",
                        "/js/**",
                        "/img/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private void validateCredentials(String username, String password) {
    if (username == null || username.length() < 5)
      throw new IllegalArgumentException("Username must be at least 5 characters long.");
    if (password == null || password.length() < 10)
      throw new IllegalArgumentException("Password must be at least 10 characters long.");
  }

  @Bean
  @Order(2)
  public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/oidc-protected", "/oauth2/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .oauth2Login(Customizer.withDefaults());
    return http.build();
  }
}
