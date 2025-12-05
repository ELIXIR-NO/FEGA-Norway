package no.elixir.e2eTests.utils;

import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class TokenUtils {

  public static String generateVisaToken(String resource, String pubKeyPath, String privKeyPath)
      throws Exception {

    HashMap<String, String> details = extractDetailsFromLSAAIToken(E2EState.env.getLSAAIToken());
    String user = details.get("sub");
    String aud = details.get("aud");
    RSAPublicKey publicKey = getPublicKey(pubKeyPath);
    RSAPrivateKey privateKey = getPrivateKey(privKeyPath);
    byte[] visaHeader = Base64.getUrlEncoder().encode(Strings.VISA_HEADER.getBytes());
    byte[] visaPayload =
        Base64.getUrlEncoder()
            .encode(String.format(Strings.VISA_PAYLOAD, user, aud, resource).getBytes());
    byte[] visaSignature = Algorithm.RSA256(publicKey, privateKey).sign(visaHeader, visaPayload);
    return "%s.%s.%s"
        .formatted(
            new String(visaHeader),
            new String(visaPayload),
            Base64.getUrlEncoder().encodeToString(visaSignature));
  }

  public static RSAPublicKey getPublicKey(String pubKeyPath) throws Exception {
    String keyContent =
        FileUtils.readFileToString(CertificateUtils.getFile(pubKeyPath), Charset.defaultCharset());

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
        FileUtils.readFileToString(CertificateUtils.getFile(privKeyPath), Charset.defaultCharset());

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");

    // Check which format we have
    if (keyContent.contains("BEGIN RSA PRIVATE KEY")) {
      // PKCS#1 format - need to convert or use BouncyCastle
      return handlePKCS1PrivateKey(keyContent);
    } else {
      // PKCS#8 format - your existing code
      String encoded =
          keyContent
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");

      byte[] decodedKey = Base64.getDecoder().decode(encoded);
      return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
    }
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
}
