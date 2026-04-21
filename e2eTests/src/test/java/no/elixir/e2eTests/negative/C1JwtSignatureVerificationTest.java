package no.elixir.e2eTests.negative;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;

/**
 * Negative security check for audit finding C1 (SECURITY-AUDIT-2026-03-27): the Elixir AAI JWT
 * signature must be verified before the proxy reads any claim from it. Today, {@code AAIAspect}
 * reads {@code aud} and {@code sub} via a base64 decode with no signature check, which lets a
 * hand-crafted token impersonate any Elixir ID.
 *
 * <p>This check forges a structurally-valid JWT — same header and claim shape as a legitimate e2e
 * token — but signs it with a freshly-generated RSA key the proxy does not trust. A correctly
 * implemented resource server rejects such a request on signature verification alone.
 *
 * <p>Currently this passes because TSD's token-exchange endpoint verifies the signature
 * downstream; if that safety net is ever removed, this test fails and we know we've lost defense in
 * depth.
 */
public class C1JwtSignatureVerificationTest {

  public static void verifyForgedJwtIsRejected() throws Exception {
    E2EState.log.info("C1 negative check: sending JWT signed by an untrusted key...");
    String forgedToken = mintTokenWithAttackerKey();

    String url =
        String.format(
            "https://%s:%s/files?inbox=true",
            E2EState.env.getProxyHost(), E2EState.env.getProxyPort());

    HttpResponse<String> response =
        Unirest.get(url)
            .basicAuth(E2EState.env.getCegaAuthUsername(), E2EState.env.getCegaAuthPassword())
            .header("Proxy-Authorization", "Bearer " + forgedToken)
            .asString();

    int status = response.getStatus();
    E2EState.log.info("C1 negative check: proxy returned HTTP {}", status);
    assertTrue(
        status == 401 || status == 403,
        String.format(
            "Forged-signature JWT must be rejected by /files but got HTTP %d. "
                + "Response body: %s. "
                + "A 2xx here means C1 is live: the proxy trusted an unverified token.",
            status, response.getBody()));
  }

  private static String mintTokenWithAttackerKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair attackerKeys = generator.generateKeyPair();

    Map<String, Object> ga4ghVisa = new HashMap<>();
    ga4ghVisa.put("asserted", Strings.VISA_ASSERTED);
    ga4ghVisa.put("by", Strings.VISA_BY);
    ga4ghVisa.put("source", Strings.VISA_SOURCE);
    ga4ghVisa.put("type", Strings.VISA_TYPE);
    ga4ghVisa.put("value", String.format(Strings.VISA_VALUE_TEMPLATE, "download"));

    return Jwts.builder()
        .header()
        .add("jku", Strings.JWT_JKU)
        .add("kid", Strings.JWT_KID)
        .add("typ", Strings.JWT_TYP)
        .add("alg", Strings.JWT_ALG)
        .and()
        .subject(Strings.JWT_SUBJECT)
        .audience()
        .add(E2EState.env.getProxyTokenAudience())
        .and()
        .claim("ga4gh_visa_v1", ga4ghVisa)
        .issuer(Strings.JWT_ISSUER)
        .expiration(new Date(Strings.JWT_EXPIRATION * 1000))
        .issuedAt(new Date(Strings.JWT_ISSUED_AT * 1000))
        .id(Strings.JWT_ID)
        .signWith(attackerKeys.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }
}
