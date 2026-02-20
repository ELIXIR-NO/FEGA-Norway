package no.elixir.fega.ltp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfig {

  private final int maxCacheSize;
  private final long timeToLive;
  private final TimeUnit timeUnit;
  private final String aaiBase;

  public JwtDecoderConfig(
      @Value("${jwk-cache.max-size}") int maxCacheSize,
      @Value("${jwk-cache.duration}") long timeToLive,
      @Value("${jwk-cache.time-unit}") TimeUnit timeUnit,
      @Value("${aai.service-base-url}") String aaiBase) {
    this.maxCacheSize = maxCacheSize;
    this.timeToLive = timeToLive;
    this.timeUnit = timeUnit;
    this.aaiBase = aaiBase;
  }

  @Bean
  public JwtDecoder jwtDecoder() {

    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
        Caffeine.newBuilder()
            .expireAfterWrite(timeToLive, timeUnit)
            .maximumSize(maxCacheSize)
            .build();

    Cache jwkCache = new CaffeineCache("jwkCache", nativeCache);

    return NimbusJwtDecoder.withJwkSetUri(aaiBase + "/oidc/jwk").cache(jwkCache).build();
  }
}
