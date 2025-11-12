package no.elixir.e2eTests.utils;

import io.jsonwebtoken.Jwts;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import org.apache.commons.io.FileUtils;

public class TokenUtils {

  public static String generateVisaToken(String resource) throws Exception {
    RSAPrivateKey privateKey = getPrivateKey();
    // Build the GA4GH visa claim
    Map<String, Object> ga4ghVisa = new HashMap<>();
    ga4ghVisa.put("asserted", Strings.VISA_ASSERTED);
    ga4ghVisa.put("by", Strings.VISA_BY);
    ga4ghVisa.put("source", Strings.VISA_SOURCE);
    ga4ghVisa.put("type", Strings.VISA_TYPE);
    ga4ghVisa.put("value", String.format(Strings.VISA_VALUE_TEMPLATE, resource));
    // Build and sign the JWT
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
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  public static String encodedPublicKey() throws Exception {
    String jwtPublicKey =
        org.apache.commons.io.FileUtils.readFileToString(
            CertificateUtils.getCertificateFile("jwt.pub.pem"), Charset.defaultCharset());
    return jwtPublicKey
        .replace(Strings.BEGIN_PUBLIC_KEY, "")
        .replace(Strings.END_PUBLIC_KEY, "")
        .replace(System.lineSeparator(), "")
        .replace(" ", "")
        .trim();
  }

  public static RSAPrivateKey getPrivateKey() throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    String jwtPublicKey =
        FileUtils.readFileToString(
            CertificateUtils.getCertificateFile("jwt.priv.pem"), Charset.defaultCharset());
    String encodedKey =
        jwtPublicKey
            .replace(Strings.BEGIN_PRIVATE_KEY, "")
            .replace(Strings.END_PRIVATE_KEY, "")
            .replace(System.lineSeparator(), "")
            .replace(" ", "")
            .trim();
    byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
    return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
  }
}
