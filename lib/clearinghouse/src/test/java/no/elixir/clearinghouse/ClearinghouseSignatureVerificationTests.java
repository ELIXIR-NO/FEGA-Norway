package no.elixir.clearinghouse;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureAlgorithm;
import io.jsonwebtoken.security.SignatureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Date;
import lombok.SneakyThrows;
import no.elixir.clearinghouse.model.Visa;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A token we cannot verify must be distinguishable from a passport that legitimately carries no
 * visas.
 *
 * <p>Both collapse to the same value today: a signature failure is logged and swallowed, so the
 * caller receives the same empty Optional or empty list either way. {@code TokenService} in the
 * proxy reads that as "this user has no access" rather than "reject this request", which is the
 * substance of ELIXIR-NO/FEGA-Norway#790.
 *
 * <p>Every forged token here is signed with a throwaway key pair generated in-process and is
 * otherwise claim-for-claim identical to the fixtures {@link CredentialsProvider} mints, so a
 * failure isolates signature verification and nothing else.
 */
public class ClearinghouseSignatureVerificationTests {

  private static final String PASSPORT_WITHOUT_VISAS =
      "{\"sub\": \"test@elixir-europe.org\", \"ga4gh_passport_v1\": []}";

  private MockWebServer mockWebServer;
  private CredentialsProvider credentialsProvider;
  private RSAPublicKey trustedPublicKey;
  private String trustedPemPublicKey;
  private String forgedVisaToken;
  private String forgedAccessToken;

  @SneakyThrows
  @BeforeEach
  public void init() {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    String baseUrl = mockWebServer.url("/").toString();

    credentialsProvider = new CredentialsProvider(baseUrl);
    trustedPublicKey = (RSAPublicKey) credentialsProvider.getPublicKey();
    trustedPemPublicKey = Files.readString(Path.of("src/test/resources/public.pem"));

    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair untrusted = generator.generateKeyPair();
    forgedVisaToken = forgeVisaToken(untrusted, baseUrl);
    forgedAccessToken = forgeAccessToken(untrusted, baseUrl);

    // Only the empty-passport case reaches the network: verification runs before the /userinfo
    // call, so a forged access token never gets that far.
    MockResponse emptyPassport =
        new MockResponse().setResponseCode(200).setBody(PASSPORT_WITHOUT_VISAS);
    mockWebServer.setDispatcher(
        new Dispatcher() {
          @NotNull @Override
          public MockResponse dispatch(RecordedRequest request) {
            return "/userinfo".equals(request.getPath())
                ? emptyPassport
                : new MockResponse().setResponseCode(404);
          }
        });
  }

  @AfterEach
  public void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  public void forgedVisaTokenIsRejected() {
    Assertions.assertThrows(
        SignatureException.class,
        () -> Clearinghouse.INSTANCE.getVisaWithPublicKey(forgedVisaToken, trustedPublicKey));
  }

  @Test
  public void forgedAccessTokenIsRejected() {
    Assertions.assertThrows(
        SignatureException.class,
        () ->
            Clearinghouse.INSTANCE.getVisaTokensWithPublicKey(forgedAccessToken, trustedPublicKey));
  }

  /** The proxy reaches the swallow through this wrapper, which must not absorb the failure. */
  @Test
  public void forgedVisaTokenIsRejectedThroughThePemWrapper() {
    Assertions.assertThrows(
        SignatureException.class,
        () -> Clearinghouse.INSTANCE.getVisaWithPEMPublicKey(forgedVisaToken, trustedPemPublicKey));
  }

  /** As above, for the token-listing half of the same call path. */
  @Test
  public void forgedAccessTokenIsRejectedThroughThePemWrapper() {
    Assertions.assertThrows(
        SignatureException.class,
        () ->
            Clearinghouse.INSTANCE.getVisaTokensWithPEMPublicKey(
                forgedAccessToken, trustedPemPublicKey));
  }

  /**
   * The other half of the contract, and the case a forgery is currently indistinguishable from: a
   * passport that verifies but holds no visas stays an empty list rather than becoming an error.
   */
  @Test
  public void passportWithoutVisasStaysEmpty() {
    Collection<String> visaTokens =
        Clearinghouse.INSTANCE.getVisaTokensWithPublicKey(
            credentialsProvider.getAccessToken(), trustedPublicKey);
    Assertions.assertTrue(visaTokens.isEmpty());
  }

  private String forgeVisaToken(KeyPair untrusted, String issuer) {
    Visa visa = new Visa();
    visa.setBy("system");
    visa.setType("AffiliationAndRole");
    visa.setAsserted(1583757401L);
    visa.setSource("https://login.elixir-czech.org/google-idp/");
    visa.setValue("affiliate@google.com");

    SignatureAlgorithm alg = Jwts.SIG.RS512;
    return Jwts.builder()
        .header()
        .keyId("rsa1")
        .type("JWT")
        .add("jku", issuer + "jwk")
        .add("alg", "RS256")
        .and()
        .signWith(untrusted.getPrivate(), alg)
        .subject("test@elixir-europe.org")
        .claim("ga4gh_visa_v1", visa)
        .issuer(issuer)
        .expiration(new Date(32503680000000L))
        .issuedAt(new Date())
        .id("f520d56f-e51a-431c-94e1-2a3f9da8b0c9")
        .compact();
  }

  private String forgeAccessToken(KeyPair untrusted, String issuer) {
    SignatureAlgorithm alg = Jwts.SIG.RS512;
    return Jwts.builder()
        .header()
        .keyId("rsa1")
        .add("alg", "RS256")
        .and()
        .signWith(untrusted.getPrivate(), alg)
        .subject("test@elixir-europe.org")
        .claim("azp", "e84ce6d6-a136-4654-8128-14f034ea24f7")
        .claim("scope", "ga4gh_passport_v1 openid")
        .audience()
        .add("e84ce6d6-a136-4654-8128-14f034ea24f7")
        .and()
        .issuer(issuer)
        .expiration(new Date(32503680000000L))
        .issuedAt(new Date())
        .id("03f5ca99-8df5-4d64-9dcb-7bf7701fe257")
        .compact();
  }
}
