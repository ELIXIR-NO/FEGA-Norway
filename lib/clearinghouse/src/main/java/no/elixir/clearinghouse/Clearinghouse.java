package no.elixir.clearinghouse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import no.elixir.clearinghouse.model.Visa;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

/**
 * Singleton class to be used for getting visa JWT tokens provided access JWT token and for
 * converting visa JWT tokens to <code>Visa</code> POJOs.
 */
@Slf4j
public enum Clearinghouse {
  INSTANCE;

  private static final String KEY_WRAPPING = "-----(.*?)-----";
  private static final String JKU = "jku";
  private static final String RSA = "RSA";
  private static final String JWKS_URI = "jwks_uri";
  private static final String GA_4_GH_PASSPORT_V_1 = "ga4gh_passport_v1";
  private static final String GA_4_GH_VISA_V_1 = "ga4gh_visa_v1";
  private static final String USERINFO = "userinfo";
  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer ";
  private static final String KID = "kid";
  private final OkHttpClient client = new OkHttpClient();

  private final Gson gson = new Gson();

  /**
   * Validates access JWT token and returns a list of Visas obtained from "/userinfo" endpoint.
   * Access token is validated based on JWKs URL of the OpenID configuration. Visa tokens are
   * validated based on JKUs.
   *
   * @param accessToken Access JWT token.
   * @param openIDConfigurationURL ".well-known/openid-configuration" full URL.
   * @return List of GA4GH Visas.
   */
  public Collection<Visa> getVisas(String accessToken, String openIDConfigurationURL) {
    return getVisaTokens(accessToken, openIDConfigurationURL).stream()
        .map(this::getVisa)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  /**
   * Validates access JWT token and returns a list of Visas obtained from "/userinfo" endpoint.
   * Access token is validated based on PEM RSA public key provided. Visa tokens are validated based
   * on JKUs.
   *
   * @param accessToken Access JWT token.
   * @param pemPublicKey PEM RSA public key.
   * @return List of GA4GH Visas.
   */
  public Collection<Visa> getVisasWithPEMPublicKey(String accessToken, String pemPublicKey) {
    return getVisaTokensWithPEMPublicKey(accessToken, pemPublicKey).stream()
        .map(this::getVisa)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  /**
   * Validates access JWT token and returns a list of Visas obtained from "/userinfo" endpoint.
   * Access token is validated based on RSA public key provided. Visa tokens are validated based on
   * JKUs.
   *
   * @param accessToken Access JWT token.
   * @param publicKey RSA public key.
   * @return List of GA4GH Visas.
   */
  public Collection<Visa> getVisasWithPublicKey(String accessToken, RSAPublicKey publicKey) {
    return getVisaTokensWithPublicKey(accessToken, publicKey).stream()
        .map(this::getVisa)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  /**
   * Validates visa JWT token and converts it to <code>Visa</code> POJO. Token is validated based on
   * JKU.
   *
   * @param visaToken Visa JWT token.
   * @return Optional <code>Visa</code> POJO: present if token validated successfully.
   */
  public Optional<Visa> getVisa(String visaToken) {
    var jku = getHeaderItemValue(visaToken, JKU);
    var keyId = getHeaderItemValue(visaToken, KID);
    var jwk = JWKProvider.INSTANCE.get(jku, keyId);
    try {
      return getVisaWithPublicKey(visaToken, (RSAPublicKey) jwk.toKey());
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  /**
   * Validates visa JWT token and converts it to <code>Visa</code> POJO. Token is validated based on
   * PEM RSA public key provided.
   *
   * @param visaToken Visa JWT token.
   * @param pemPublicKey PEM RSA public key.
   * @return Optional <code>Visa</code> POJO: present if token validated successfully.
   */
  public Optional<Visa> getVisaWithPEMPublicKey(String visaToken, String pemPublicKey) {
    try {
      return getVisaWithPublicKey(visaToken, readPEMKey(pemPublicKey));
    } catch (GeneralSecurityException e) {
      log.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  /**
   * Validates visa JWT token and converts it to <code>Visa</code> POJO. Token is validated based on
   * RSA public key provided.
   *
   * @param visaToken Visa JWT token.
   * @param publicKey RSA public key.
   * @return Optional <code>Visa</code> POJO: present if token validated successfully.
   */
  public Optional<Visa> getVisaWithPublicKey(String visaToken, RSAPublicKey publicKey) {
    try {
      byte[] encoded = publicKey.getEncoded();
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      PublicKey pubKey = keyFactory.generatePublic(keySpec);

      Jws<Claims> jws = Jwts.parser().verifyWith(pubKey).build().parseSignedClaims(visaToken);
      Claims claims = jws.getPayload();
      if (claims.containsKey(GA_4_GH_VISA_V_1)) {
        String visaJson = new Gson().toJson(claims.get(GA_4_GH_VISA_V_1));
        Visa visa = new Gson().fromJson(visaJson, Visa.class);
        visa.setSub(jws.getPayload().getSubject());
        visa.setRawToken(visaToken);
        return Optional.of(visa);
      }
    } catch (SignatureException e) {
      log.error("Invalid signature in visa token", e);
    } catch (JsonSyntaxException e) {
      log.error("Invalid JSON syntax in visa claim", e);
    } catch (Exception e) {
      log.error("Error parsing or verifying visa token", e);
    }
    return Optional.empty();
  }

  /**
   * Validates access JWT token and returns a list of visa JWT tokens from "/userinfo" endpoint.
   * Access token is validated based on JWKs URL of the OpenID configuration.
   *
   * @param accessToken Access JWT token.
   * @param openIDConfigurationURL ".well-known/openid-configuration" full URL.
   * @return List of visa JWT tokens.
   */
  public Collection<String> getVisaTokens(String accessToken, String openIDConfigurationURL) {
    Request request = new Request.Builder().url(openIDConfigurationURL).get().build();

    try {
      ResponseBody body = client.newCall(request).execute().body();
      assert body != null;
      String bodyString = body.string();
      var jwksURL = gson.fromJson(bodyString, JsonObject.class).get(JWKS_URI).getAsString();

      var keyId = getHeaderItemValue(accessToken, KID);
      var jwk = JWKProvider.INSTANCE.get(jwksURL, keyId);

      return getVisaTokensWithPublicKey(accessToken, (RSAPublicKey) jwk.toKey());
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  /**
   * Validates access JWT token and returns a list of visa JWT tokens from "/userinfo" endpoint.
   * Access token is validated based on PEM RSA public key provided.
   *
   * @param accessToken Access JWT token.
   * @param pemPublicKey PEM RSA public key.
   * @return List of visa JWT tokens.
   */
  public Collection<String> getVisaTokensWithPEMPublicKey(String accessToken, String pemPublicKey) {
    try {
      return getVisaTokensWithPublicKey(accessToken, readPEMKey(pemPublicKey));
    } catch (GeneralSecurityException e) {
      log.error(e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  /**
   * Validates access JWT token and returns a list of visa JWT tokens from "/userinfo" endpoint.
   * Access token is validated based on RSA public key provided.
   *
   * @param accessToken Access JWT token.
   * @param publicKey RSA public key.
   * @return List of visa JWT tokens.
   */
  public Collection<String> getVisaTokensWithPublicKey(String accessToken, RSAPublicKey publicKey) {
    try {
      byte[] encoded = publicKey.getEncoded();
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      PublicKey pubKey = keyFactory.generatePublic(keySpec);

      Jws<Claims> jws = Jwts.parser().verifyWith(pubKey).build().parseSignedClaims(accessToken);
      String userInfoEndpoint = jws.getPayload().getIssuer() + USERINFO;
      Request request =
          new Request.Builder()
              .header(AUTHORIZATION, BEARER + accessToken)
              .url(userInfoEndpoint)
              .get()
              .build();

      ResponseBody body = client.newCall(request).execute().body();
      assert body != null;
      String bodyString = body.string();
      var passport =
          gson.fromJson(bodyString, JsonObject.class).getAsJsonArray(GA_4_GH_PASSPORT_V_1);

      return passport.asList().stream().map(x -> x.toString().replaceAll("\"", "")).toList();

    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (SignatureException e) {
      log.error("Invalid signature in visa token", e);
    } catch (JsonSyntaxException e) {
      log.error("Invalid JSON syntax in visa claim", e);
    } catch (Exception e) {
      log.error("Error parsing or verifying visa token", e);
    }

    return Collections.emptyList();
  }

  /**
   * Returns a list of visa JWT tokens from "/userinfo" endpoint provided the opaque access token.
   *
   * @param accessToken Opaque access token.
   * @param userInfoEndpoint "/userinfo" endpoint URL.
   * @return List of visa JWT tokens.
   */
  public Collection<String> getVisaTokensFromOpaqueToken(
      String accessToken, String userInfoEndpoint) {
    Request request =
        new Request.Builder()
            .header(AUTHORIZATION, BEARER + accessToken)
            .url(userInfoEndpoint)
            .get()
            .build();

    try {
      ResponseBody body = client.newCall(request).execute().body();
      assert body != null;
      String bodyString = body.string();
      var passport =
          gson.fromJson(bodyString, JsonObject.class).getAsJsonArray(GA_4_GH_PASSPORT_V_1);
      return passport.asList().stream().map(x -> x.toString().replaceAll("\"", "")).toList();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private RSAPublicKey readPEMKey(String publicKey) throws GeneralSecurityException {
    KeyFactory keyFactory = KeyFactory.getInstance(RSA);
    publicKey =
        publicKey
            .replaceAll(KEY_WRAPPING, "")
            .replace(System.lineSeparator(), "")
            .replace(" ", "")
            .trim();
    var decodedKey = Base64.getDecoder().decode(publicKey);
    return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
  }

  private String getHeaderItemValue(String token, String key) {
    var tokenArray = token.split("[.]");
    byte[] decodedHeader = Base64.getUrlDecoder().decode(tokenArray[0]);
    String decodedHeaderString = new String(decodedHeader);
    return gson.fromJson(decodedHeaderString, JsonObject.class).get(key).getAsString();
  }
}
