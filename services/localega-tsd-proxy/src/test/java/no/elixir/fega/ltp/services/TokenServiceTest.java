package no.elixir.fega.ltp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
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
   * check itself; asserting {@link SignatureException} (not a broader exception) pins the rejection
   * to that check.
   */
  @Test
  void parseVerified_rejectsTokenSignedByUntrustedKey() throws Exception {
    KeyPair untrusted = generateRsaKeyPair();

    assertThatThrownBy(() -> tokenService.parseVerified(visaShapedToken(untrusted)))
        .isInstanceOf(SignatureException.class);
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

  private static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    return kpg.generateKeyPair();
  }
}
