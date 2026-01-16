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

  @Bean
  public JwtDecoder jwtDecoder(@Value("${aai.service-base-url}") String aaiBase) {

    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
        Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.MINUTES).maximumSize(100).build();

    Cache jwkCache = new CaffeineCache("jwkCache", nativeCache);

    return NimbusJwtDecoder.withJwkSetUri(aaiBase + "/oidc/jwk").cache(jwkCache).build();
  }
}
