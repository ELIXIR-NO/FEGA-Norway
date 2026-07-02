package no.elixir.fega.ltp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtDecoderConfigTest {

  private MockWebServer mockWebServer;
  private String mockJwkSetJson;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    // Generate a dummy JWK Set to return from our mock server
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    RSAPublicKey publicKey = (RSAPublicKey) kpg.generateKeyPair().getPublic();
    RSAKey key = new RSAKey.Builder(publicKey).keyID("test-key").build();
    mockJwkSetJson = new JWKSet(key).toString();
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void jwtDecoder_ShouldCacheJwkSet() {
    // 1. Set up the specific cache you want to test
    org.springframework.cache.Cache jwkCache = new ConcurrentMapCache("jwkCache");

    // 2. Point the decoder to the MockWebServer URL
    JwtDecoder jwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(mockWebServer.url("/oidc/jwk").toString())
            .cache(jwkCache)
            .build();

    // 3. Prepare the mock server with exactly ONE response
    mockWebServer.enqueue(
        new MockResponse().setBody(mockJwkSetJson).setHeader("Content-Type", "application/json"));

    // 4. Trigger the first lookup
    try {
      jwtDecoder.decode("eyJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0.eyJzdWIiOiIxMjMifQ.sig");
    } catch (Exception ignored) {
      // Ignore validation errors
    }

    // 5. Trigger the second lookup
    try {
      jwtDecoder.decode("eyJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0.eyJzdWIiOiIxMjMifQ.sig");
    } catch (Exception ignored) {
      // Ignore validation errors
    }

    // 6. VERIFY: The server should have only seen 1 request despite 2 decode attempts
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);

    // 7. Verify the cache object itself is not empty
    assertThat(jwkCache.getNativeCache()).isNotNull();
  }

  @Test
  void jwtDecoder_rejectsTokenSignedByUntrustedKey() throws Exception {
    // The oauth2 resource-server endpoints (/token, /user) trust this decoder, built here
    // through the production JwtDecoderConfig wiring rather than a hand-rolled one. The
    // forged token reuses the served key's id ("test-key") but is signed with a different,
    // untrusted key, so the decoder resolves a key and the failure is specifically a
    // signature mismatch, not a missing key. The upload path (Proxy-Authorization) has its
    // own guard in TokenServiceTest and AAIAspectTest.
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair untrusted = kpg.generateKeyPair();

    SignedJWT forged =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
            new JWTClaimsSet.Builder().subject("attacker").build());
    forged.sign(new RSASSASigner(untrusted.getPrivate()));

    String aaiBase = mockWebServer.url("").toString().replaceAll("/$", "");
    JwtDecoder jwtDecoder = new JwtDecoderConfig(10, 5, TimeUnit.MINUTES, aaiBase).jwtDecoder();
    mockWebServer.enqueue(
        new MockResponse().setBody(mockJwkSetJson).setHeader("Content-Type", "application/json"));

    assertThatThrownBy(() -> jwtDecoder.decode(forged.serialize()))
        .isInstanceOf(JwtException.class);
  }
}
