package no.elixir.fega.ltp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import no.elixir.fega.ltp.errors.ErrorCode;
import no.elixir.fega.ltp.errors.ProxyException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

  @TempDir Path tempDir;

  private TokenService tokenService;
  private KeyPair trusted;

  @BeforeEach
  void setUp() throws Exception {
    trusted = generateRsaKeyPair();

    // A readable PEM keeps parseVerified on the static-key branch; an unreadable path
    // would silently fall back to network JWKS discovery and break test isolation.
    Path pem = tempDir.resolve("passport-public-key.pem");
    Files.writeString(
        pem,
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(trusted.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----\n");

    tokenService = new TokenService();
    ReflectionTestUtils.setField(tokenService, "passportPublicKeyPath", pem.toString());
  }

  /**
   * Audit control C1: a structurally valid, unexpired token signed by a key outside the trusted key
   * set must be refused. Every claim is valid, so the only thing that can fail is the signature
   * check itself; asserting a {@link SignatureException} cause (not a broader exception) pins the
   * rejection to that check.
   */
  @Test
  void parseVerified_rejectsTokenSignedByUntrustedKey() throws Exception {
    KeyPair untrusted = generateRsaKeyPair();

    assertThatThrownBy(() -> tokenService.parseVerified(visaShapedToken(untrusted)))
        .isInstanceOfSatisfying(
            ProxyException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ELIXIR_AAI_TOKEN_INVALID))
        .hasCauseInstanceOf(SignatureException.class);
  }

  /**
   * JWKS mode, which runs wherever the passport PEM is unreadable: a token whose kid the key set
   * does not hold is the caller's forgery, not a server fault. See ELIXIR-NO/FEGA-Norway#913.
   */
  @Test
  void parseVerified_viaJwks_rejectsTokenWithAnUnknownKid() throws Exception {
    try (MockWebServer issuer = jwksIssuer("known-kid")) {
      useJwks(issuer);

      assertThatThrownBy(() -> tokenService.parseVerified(tokenWithKid(trusted, "unknown-kid")))
          .isInstanceOfSatisfying(
              ProxyException.class,
              e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ELIXIR_AAI_TOKEN_INVALID));
    }
  }

  @Test
  void parseVerified_viaJwks_acceptsTokenWithAKnownKid() throws Exception {
    try (MockWebServer issuer = jwksIssuer("known-kid")) {
      useJwks(issuer);

      Claims claims = tokenService.parseVerified(tokenWithKid(trusted, "known-kid"));

      assertThat(claims.getSubject()).isEqualTo("dummy@elixir-europe.org");
    }
  }

  @Test
  void parseVerified_viaJwks_reportsAnUnreachableIssuerAsUpstream() throws Exception {
    MockWebServer gone = new MockWebServer();
    gone.start();
    useJwks(gone);
    gone.shutdown();

    assertThatThrownBy(() -> tokenService.parseVerified(tokenWithKid(trusted, "known-kid")))
        .isInstanceOfSatisfying(
            ProxyException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UPSTREAM_FAILED));
  }

  @Test
  void getControlledAccessGrantsVisas_rejectsAMalformedToken() {
    assertThatThrownBy(() -> tokenService.getControlledAccessGrantsVisas("not-a-jwt"))
        .isInstanceOfSatisfying(
            ProxyException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ELIXIR_AAI_TOKEN_INVALID));
  }

  /**
   * Positive control for the rejection test: proves the trusted-key wiring works, so the forged
   * token above fails on its signature and not on a broken fixture.
   */
  @Test
  void parseVerified_returnsClaimsForTokenSignedByTrustedKey() {
    Claims claims = tokenService.parseVerified(visaShapedToken(trusted));

    assertThat(claims.getSubject()).isEqualTo("dummy@elixir-europe.org");
  }

  private String visaShapedToken(KeyPair signer) {
    return Jwts.builder()
        .subject("dummy@elixir-europe.org")
        .issuer("https://login.elixir-czech.org/oidc/")
        .expiration(new Date(32503680000000L))
        .claim(
            "ga4gh_visa_v1",
            Map.of(
                "type", "ControlledAccessGrants",
                "asserted", 1583757401L,
                "value", "https://ega-archive.org/datasets/EGAD00010000919",
                "source", "https://ega-archive.org/dacs/EGAC00000000001",
                "by", "dac"))
        .signWith(signer.getPrivate())
        .compact();
  }

  private void useJwks(MockWebServer issuer) {
    ReflectionTestUtils.setField(
        tokenService, "passportPublicKeyPath", tempDir.resolve("absent.pem").toString());
    ReflectionTestUtils.setField(
        tokenService,
        "openIDConfigurationURL",
        issuer.url("/.well-known/openid-configuration").toString());
  }

  /** An issuer serving its OpenID configuration and a key set holding the trusted key as kid. */
  private MockWebServer jwksIssuer(String kid) throws IOException {
    MockWebServer issuer = new MockWebServer();
    RSAPublicKey key = (RSAPublicKey) trusted.getPublic();
    String jwks =
        "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\""
            + kid
            + "\",\"n\":\""
            + base64Url(key.getModulus())
            + "\",\"e\":\""
            + base64Url(key.getPublicExponent())
            + "\"}]}";
    issuer.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            String body =
                request.getPath().endsWith("/jwks")
                    ? jwks
                    : "{\"jwks_uri\":\"" + issuer.url("/jwks") + "\"}";
            return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
          }
        });
    issuer.start();
    return issuer;
  }

  private static String base64Url(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes[0] == 0) {
      bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String tokenWithKid(KeyPair signer, String kid) {
    return Jwts.builder()
        .header()
        .keyId(kid)
        .and()
        .subject("dummy@elixir-europe.org")
        .expiration(new Date(32503680000000L))
        .signWith(signer.getPrivate())
        .compact();
  }

  private static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    return kpg.generateKeyPair();
  }
}
