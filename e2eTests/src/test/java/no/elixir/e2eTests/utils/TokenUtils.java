package no.elixir.e2eTests.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.StringReader;
import io.jsonwebtoken.Jwts;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Date;
import java.util.Map;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class TokenUtils {

    public static String generateVisaToken(String resource, String pubKeyPath, String privKeyPath) throws Exception {
        RSAPrivateKey privateKey = getPrivateKey(privKeyPath);
        String user, aud;
        // if the passport scoped access token is present and if the
        // integration is set to EGA_DEV environment, we get some
        // details from that provided token.
        if (E2EState.env.getLSAAIToken() != null
                && !E2EState.env.getLSAAIToken().isEmpty()
                && E2EState.env.getIntegration().equals("EGA_DEV")) {
          HashMap<String, String> details = extractDetailsFromLSAAIToken(E2EState.env.getLSAAIToken());
          user = details.get("sub");
          aud = details.get("aud");
        } else {
          user = Strings.JWT_SUBJECT;
          aud = E2EState.env.getProxyTokenAudience();
        }
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
                .subject(user)
                .audience()
                .add(aud)
                .and()
                .claim("ga4gh_visa_v1", ga4ghVisa)
                .issuer(Strings.JWT_ISSUER)
                .expiration(new Date(Strings.JWT_EXPIRATION * 1000))
                .issuedAt(new Date(Strings.JWT_ISSUED_AT * 1000))
                .id(Strings.JWT_ID)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

  public static RSAPublicKey getPublicKey(String pubKeyPath) throws Exception {
      String keyContent =
              FileUtils.readFileToString(E2EState.env.getIntegration().equals("EGA_DEV")
                      ? CertificateUtils.getFile(pubKeyPath)
                      : CertificateUtils.getCertificateFile(pubKeyPath), Charset.defaultCharset());

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");

    // Remove headers/footers and clean up
    String encoded =
        keyContent
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "") // Handle RSA format too
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replaceAll("\\s", ""); // Remove all whitespace

    byte[] decodedKey = Base64.getDecoder().decode(encoded);
    return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
  }

  public static RSAPrivateKey getPrivateKey(String privKeyPath) throws Exception {
    String keyContent =
        FileUtils.readFileToString(E2EState.env.getIntegration().equals("EGA_DEV")
                ? CertificateUtils.getFile(privKeyPath)
                : CertificateUtils.getCertificateFile(privKeyPath), Charset.defaultCharset());

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");

    if (keyContent.contains("BEGIN RSA PRIVATE KEY")) {
      // PKCS#1 format
      return handlePKCS1PrivateKey(keyContent);
    } else {
      // PKCS#8 format
      String encoded =
          keyContent
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");

      byte[] decodedKey = Base64.getDecoder().decode(encoded);
      return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
    }
  }

  /**
   * Introduced with EGA_DEV runtime tests. Extracts specific details
   * from a given passport scoped access token.
   *
   * @param passportScopedAccessToken token to decode
   * @return Map containing the `sub` and `aud`
   */
  public static HashMap<String, String> extractDetailsFromLSAAIToken(
      String passportScopedAccessToken) {
    var tokenArray = passportScopedAccessToken.split("[.]");
    byte[] decodedPayload = Base64.getUrlDecoder().decode(tokenArray[1]);
    String decodedPayloadString = new String(decodedPayload);
    JsonObject claims = new Gson().fromJson(decodedPayloadString, JsonObject.class);
    return new HashMap<>() {
      {
        put("sub", claims.get("sub").getAsString());
        put("aud", claims.get("aud").getAsString());
      }
    };
  }

  private static RSAPrivateKey handlePKCS1PrivateKey(String keyContent) throws Exception {
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

    PEMParser pemParser = new PEMParser(new StringReader(keyContent));
    Object object = pemParser.readObject();
    pemParser.close();

    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

    if (object instanceof org.bouncycastle.openssl.PEMKeyPair pemKeyPair) {
      // This is the case for PKCS#1 RSA private keys
      PrivateKeyInfo privateKeyInfo = pemKeyPair.getPrivateKeyInfo();
      return (RSAPrivateKey) converter.getPrivateKey(privateKeyInfo);
    } else if (object instanceof PrivateKeyInfo) {
      // This is for PKCS#8 format
      return (RSAPrivateKey) converter.getPrivateKey((PrivateKeyInfo) object);
    } else if (object instanceof org.bouncycastle.asn1.pkcs.RSAPrivateKey rsaPrivKey) {
      org.bouncycastle.asn1.x509.AlgorithmIdentifier algId =
              new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                      org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.rsaEncryption);
      PrivateKeyInfo privKeyInfo = new PrivateKeyInfo(algId, rsaPrivKey);
      return (RSAPrivateKey) converter.getPrivateKey(privKeyInfo);
    }

    throw new IllegalArgumentException(
            "Unable to parse private key. Object type: "
                    + (object != null ? object.getClass().getName() : "null"));
  }

}
