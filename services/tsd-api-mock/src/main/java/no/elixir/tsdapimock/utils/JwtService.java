package no.elixir.tsdapimock.utils;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {

  private final String RSA_PUBLIC_VERIFICATION_KEYS_PATH = "/etc/jwt/public_keys";
  private String SIGNING_SECRET;

  @Value("${tsd.file.signingSecret}")
  public void setSigningSecret(String signingSecret) {
    this.SIGNING_SECRET = signingSecret;
  }

  /**
   * Creates a JSON Web Token (JWT) with the given parameters.
   *
   * @param project the name of the project
   * @param id the ID of the token
   * @param issuer the issuer of the token
   * @param subject the subject of the token
   * @param ttlMillis the time to live (TTL) in milliseconds
   * @return the created JWT
   */
  public String createJwt(
      String project, String id, String issuer, String subject, long ttlMillis) {
    Key key = Keys.hmacShaKeyFor(SIGNING_SECRET.getBytes());
    var tokenBuilder =
        Jwts.builder()
            .id(id)
            .issuer(issuer)
            .subject(subject)
            .claim("user", project + "-" + subject)
            .signWith(key);
    var nowMillis = System.currentTimeMillis();
    if (ttlMillis > 0) {
      var expMillis = nowMillis + ttlMillis;
      var exp = new Date(expMillis);
      tokenBuilder.expiration(exp);
    }
    return tokenBuilder.compact();
  }

  // TODO: verify the issued token from TSD
  public boolean verify(String authorizationHeader) {
    return true;
  }

  /**
   * Retrieves the subject from a JSON Web Token (JWT) using the RSA public verification key. The
   * method parses and verifies the token, then extracts the subject from its payload.
   *
   * @param token the JWT token as a String.
   * @return the subject of the token.
   * @throws JwtException if an error occurs while parsing or verifying the token.
   * @throws RuntimeException if there is an issue with retrieving the RSA public verification key.
   */
  public String getElixirAAITokenSubject(String token) {
    try {
      String elixirAAIJwtToken =
          Files.readString(Paths.get(RSA_PUBLIC_VERIFICATION_KEYS_PATH, "elixir_aai.pem"));
      return Jwts.parser()
          .verifyWith(getRsaPublicSigningKey(elixirAAIJwtToken))
          .build()
          .parseSignedClaims(token)
          .getPayload()
          .getSubject();
    } catch (JwtException e) {
      log.error(e.getMessage());
      throw new JwtException(e.getMessage());
    } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
      log.error(e.getMessage());
      throw new RuntimeException(e);
    }
  }

  /**
   * Generates a {@link PublicKey} from a PEM formatted signing key. The method normalizes the
   * provided PEM key by removing headers, footers, and any whitespace, then decodes it from Base64
   * and constructs an RSA {@link PublicKey}.
   *
   * @param signingKey the PEM formatted signing key as a String, which includes the header, footer,
   *     and possibly whitespace characters such as newlines and spaces.
   * @return a {@link PublicKey} generated from the provided PEM key.
   * @throws NoSuchAlgorithmException if the RSA algorithm is not available in the environment.
   * @throws InvalidKeySpecException if the provided key specification is invalid.
   * @throws IOException if an I/O error occurs during key processing.
   */
  public PublicKey getRsaPublicSigningKey(String signingKey)
      throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    byte[] decodedKey = Base64.getDecoder().decode(normalizePEMKey(signingKey));
    return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
  }

  /**
   * Normalizes a PEM formatted key by removing the header and footer lines and any whitespace. This
   * method handles both public and private keys, including RSA private keys.
   *
   * @param pemKey the PEM formatted key as a String, which includes the header, footer, and
   *     possibly whitespace characters such as newlines and spaces.
   * @return a normalized String representing the key without headers, footers, and whitespace.
   */
  public String normalizePEMKey(String pemKey) {
    return pemKey
        // Remove header and footer lines for different types of keys
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
        // Remove any newline characters and spaces
        .replaceAll("\\s", "")
        .trim();
  }
}
