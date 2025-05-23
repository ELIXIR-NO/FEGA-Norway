package no.elixir.fega.ltp.services;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.jsonwebtoken.Claims;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import no.elixir.clearinghouse.Clearinghouse;
import no.elixir.clearinghouse.model.Visa;
import no.elixir.clearinghouse.model.VisaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TokenService {

  private static final Pattern BASE64URL_PATTERN = Pattern.compile("^[A-Za-z0-9-_]+={0,2}$");

  @Value("${ga4gh.passport.openid-configuration-url}")
  private String openIDConfigurationURL;

  @Value("${ga4gh.passport.public-key-path}")
  private String passportPublicKeyPath;

  @Value("${ga4gh.visa.public-key-path}")
  private String visaPublicKeyPath;

  /**
   * Retrieves a list of Visa objects of type ControlledAccessGrants based on the provided JWT
   * token.
   *
   * <p>This method supports two scenarios:
   *
   * <p>1. Direct Visa Token Input: If the JWT token includes the ga4gh_visa_v1 claim, it is treated
   * as a direct Visa token. This approach is commonly used in our end-to-end (E2E) testing setup,
   * where the proxy accepts a Visa token directly for testing and debugging purposes. In this case,
   * the Visa token is verified using verifyVisaTokenAndTransformToVisaObject, and the resulting
   * Visa object is added to the list.
   *
   * <p>2. Passport-Scoped Access Token Input: In production environments, users provide a
   * Passport-Scoped Access Token. These tokens do not include the ga4gh_visa_v1 claim. Instead, the
   * method fetches Visa tokens by calling fetchThePassportUsingAccessTokenAndGetVisas. This
   * function validates the Passport-Scoped Access Token using either a PEM public key or an OpenID
   * Connect configuration URL, retrieves Visa tokens from the /userinfo endpoint, and transforms
   * them into Visa objects.
   *
   * <p>---
   *
   * <p>The method first checks if the ga4gh_visa_v1 claim is present in the JWT token: - If the
   * claim is present, the token is treated as a direct Visa token, verified, and transformed. - If
   * the claim is absent, the token is treated as a Passport-Scoped Access Token, and Visa tokens
   * are fetched accordingly.
   *
   * <p>After retrieving the Visa tokens, the method filters the resulting Visa objects to include
   * only those of type ControlledAccessGrants.
   *
   * <p>This method is designed to support both E2E testing and production scenarios: - In E2E
   * setups, direct Visa tokens can be used to simplify testing and debugging. - In production,
   * Passport-Scoped Access Tokens are used, ensuring compatibility with standard token issuance
   * processes and dynamic retrieval of Visa tokens.
   *
   * <p>By verifying Visa tokens in both cases, the method ensures that only valid and trusted
   * tokens are processed. This is the mechanism for handling Visa tokens in proxy service.
   *
   * @param jwtToken the JWT token, which can be either a direct Visa token or a Passport-Scoped
   *     Access Token
   * @return a list of Visa objects of type ControlledAccessGrants
   * @throws IllegalArgumentException if the JWT token is invalid or cannot be verified
   */
  public List<Visa> getControlledAccessGrantsVisas(String jwtToken) {
    JsonObject claims = extractFragmentFromJWT(jwtToken, TokenService.TokenFragment.BODY);
    boolean isVisa = claims.keySet().contains("ga4gh_visa_v1");
    Collection<Visa> visas = new ArrayList<>();
    if (isVisa) {
      verifyVisaTokenAndTransformToVisaObject(jwtToken).ifPresent(visas::add);
    } else {
      visas.addAll(fetchTheFullPassportUsingPassportScopedAccessTokenAndGetVisas(jwtToken));
    }
    return filterByVisaType(visas.stream().toList(), VisaType.ControlledAccessGrants);
  }

  /**
   * Extracts the subject (sub) claim from the provided JWT token.
   *
   * <p>This method decodes the body fragment of the JWT token to retrieve the subject claim, which
   * typically identifies the principal that issued the token.
   *
   * @param jwtToken the JWT token from which to extract the subject.
   * @return the subject claim (sub) as a {@link String}.
   * @throws NullPointerException if the JWT does not contain a subject claim.
   */
  public String getSubject(String jwtToken) throws IllegalArgumentException {
    JsonObject claims = extractFragmentFromJWT(jwtToken, TokenService.TokenFragment.BODY);
    return claims.get(Claims.SUBJECT).getAsString();
  }

  /**
   * Extracts the audience (aud) claim from the provided JWT token.
   *
   * <p>This method decodes the body fragment of the JWT token to retrieve the audience claim, which
   * identifies the recipient for which the token is intended.
   *
   * @param jwtToken the JWT token from which to extract the audience.
   * @return the audience claim (aud) as a {@link String} or null if the audience claim is missing.
   */
  public String getAudience(String jwtToken) throws IllegalArgumentException {
    JsonObject claims = extractFragmentFromJWT(jwtToken, TokenService.TokenFragment.BODY);
    JsonElement audience = claims.get(Claims.AUDIENCE);
    return (audience != null) ? audience.getAsString() : null;
  }

  /**
   * Extracts a specific fragment from a JWT Token.
   *
   * <p>This method splits the JWT token into its constituent parts (header, payload, and
   * signature), decodes the selected fragment (as specified by the {@link TokenFragment}), and
   * deserializes it into a {@link JsonObject}.
   *
   * @param jwtToken the JWT token to extract a fragment from; must be a valid JWS-encoded JWT.
   * @param tokenFragment the fragment to extract, specified using the {@link TokenFragment} enum.
   *     The enum values correspond to the ordinal positions of the fragments in the JWT (0 for
   *     header, 1 for payload, 2 for signature).
   * @return a {@link JsonObject} representing the decoded fragment.
   * @throws IllegalArgumentException if the JWT token format is invalid or the specified fragment
   *     is missing.
   */
  private JsonObject extractFragmentFromJWT(String jwtToken, TokenFragment tokenFragment)
      throws IllegalArgumentException {
    var fragments = jwtToken.split("[.]");
    String fragment = fragments[tokenFragment.ordinal()];
    if (!isValidBase64Url(fragment)) {
      throw new IllegalArgumentException("Invalid Base64 URL string");
    }
    byte[] decodedPayload = Base64.getUrlDecoder().decode(fragment);
    String decodedPayloadString = new String(decodedPayload);
    return new Gson().fromJson(decodedPayloadString, JsonObject.class);
  }

  /**
   * Fetches Visa tokens associated with the provided Passport-Scoped Access Token.
   *
   * <p>This method validates the Passport-Scoped Access Token using one of two approaches:
   *
   * <p>Static RSA Public Key Validation: Attempts to read a locally stored RSA public key from the
   * path specified by {@code passportPublicKeyPath}. If the file is found and readable, the method
   * uses this key to validate the token.
   *
   * <p>Dynamic OpenID Connect JKU Validation: If the RSA public key file is unavailable or
   * unreadable, the method falls back to using the OpenID Connect configuration URL ({@code
   * openIDConfigurationURL}) to validate the token. This approach dynamically retrieves public key
   * via the JWKs URL.
   *
   * <p>After validating the Passport-Scoped Access Token, the method fetches Visa tokens from the
   * `/userinfo` endpoint. Each Visa token is then verified and transformed into a {@link Visa}
   * object. Only valid Visa tokens are included in the final result.
   *
   * <p>To get a better understanding please refer to the docs and playground mentioned in links.
   *
   * @param passportScopedAccessToken the Passport-Scoped Access Token (a JWS-encoded JWT) used to
   *     authenticate and retrieve Visa tokens. It must be valid and include the `ga4gh_passport_v1`
   *     scope.
   * @return a {@link List} of {@link Visa} objects representing the verified Visa tokens.
   * @throws IllegalArgumentException if the access token is invalid, cannot be verified, or if
   *     required public key files are missing or unreadable.
   * @link <a href="https://ga4gh.github.io/data-security/aai-openid-connect-profile">docs</a>
   * @link <a href="https://ga4gh-echo.aai.lifescience-ri.eu/index.html">playground</a>
   */
  public List<Visa> fetchTheFullPassportUsingPassportScopedAccessTokenAndGetVisas(
      String passportScopedAccessToken) {

    // Collection to store Visa tokens retrieved based on the verification mode.
    Collection<String> visas;

    try {
      // Read the public key from a file located at `passportPublicKeyPath`.
      String passportPublicKey = Files.readString(Path.of(passportPublicKeyPath));
      log.info("Using configured public key mode for Visa token verification.");
      // Retrieve Visa tokens by validating the Passport-Scoped Access Token with the PEM public
      // key.
      visas =
          Clearinghouse.INSTANCE.getVisaTokensWithPEMPublicKey(
              passportScopedAccessToken, passportPublicKey);
    } catch (IOException e) {
      log.info(
          "Using OpenID Connect JKU for passport scoped access token verification. Reason: {}",
          e.getMessage());
      // Retrieve Visa tokens by validating with the OpenID Connect configuration.
      visas =
          Clearinghouse.INSTANCE.getVisaTokens(passportScopedAccessToken, openIDConfigurationURL);
    }

    // Stream processing: Verify and transform each Visa token into a Visa object.
    return visas.stream()
        .map(this::verifyVisaTokenAndTransformToVisaObject) // Verify and transform Visa token.
        .filter(Optional::isPresent) // Filter out invalid or unverified Visa tokens.
        .map(Optional::get) // Unwrap the Optional to get the Visa object.
        .collect(Collectors.toList()); // Collect the valid Visa objects into a list.
  }

  /**
   * Verifies a Visa token and transforms it into a {@link Visa} object.
   *
   * <p>This method attempts to verify the provided Visa token using two different approaches:
   *
   * <p>Static RSA Public Key Validation: If the {@code visaPublicKeyPath} file is available and
   * readable, the method assumes the Visa token is issued by a single trusted party. It uses the
   * static RSA public key contained in the file to verify the token. This approach is suitable for
   * visa(s) with a fixed issuer.
   *
   * <p>Dynamic JWK Validation: If the static public key file is not found or cannot be read, the
   * method falls back to using the JWK endpoint specified in the Visa token's header (`jku`). This
   * allows the application to dynamically validate tokens from multiple potential issuers.
   *
   * <p>If the Visa token is successfully validated, it is transformed into a {@link Visa} object.
   * Otherwise, an empty {@link Optional} is returned.
   *
   * <p>Note: The static public key mode assumes that all Visa tokens are issued by a single trusted
   * party. This constraint does not apply when using the JWK endpoint, which dynamically supports
   * multiple issuers.
   *
   * @param visaToken the Visa token (a JWS-encoded JWT) to be verified and transformed.
   * @return an {@link Optional} containing the {@link Visa} object if the token is valid;
   *     otherwise, an empty {@link Optional}.
   * @throws IllegalArgumentException if the Visa token cannot be verified due to issues such as
   *     invalid token format, missing claims, or failure to access required resources (e.g., public
   *     key file or JWK endpoint).
   */
  private Optional<Visa> verifyVisaTokenAndTransformToVisaObject(String visaToken) {
    // Conditional logic for Visa token verification.
    try { // PUBLIC_KEY mode uses a local PEM public key.
      // Read the public key from a file located at `visaPublicKeyPath`.
      String visaPublicKey = Files.readString(Path.of(visaPublicKeyPath));
      // Validate and transform the Visa token using the PEM public key.
      return Clearinghouse.INSTANCE.getVisaWithPEMPublicKey(visaToken, visaPublicKey);

    } catch (IOException e) { // JKU mode uses JWK endpoint for token validation.
      // Validate and transform the Visa token using the JWK endpoint.
      return Clearinghouse.INSTANCE.getVisa(visaToken);
    }
  }

  /**
   * Filters Visa objects by the specified VisaType.
   *
   * <p>If {@code visaType} is {@code null}, all Visa objects are considered valid. Otherwise, only
   * those matching the specified {@link VisaType} are included.
   *
   * @param visas the {@link List<Visa>} visas to filter.
   * @param visaType the {@link VisaType} to filter by; if {@code null}, all Visa types are
   *     included.
   * @return {@code List<Visa>} filtered visas.
   */
  public List<Visa> filterByVisaType(List<Visa> visas, VisaType visaType) {
    if (visaType == null) {
      return visas; // Include all types if no specific type is specified.
    }
    return visas.stream()
        .filter(v -> v.getType().equalsIgnoreCase(visaType.name()))
        .collect(Collectors.toList());
  }

  /**
   * Validates whether the given input string is a valid Base64 URL-encoded string.
   *
   * <p>A valid Base64 URL string contains only alphanumeric characters, hyphens ('-'), underscores
   * ('_'), and at most two '=' padding characters. The input is checked against a regular
   * expression to ensure it conforms to these rules.
   *
   * @param input the string to validate
   * @return {@code true} if the input is a valid Base64 URL-encoded string, {@code false} otherwise
   */
  private boolean isValidBase64Url(String input) {
    return BASE64URL_PATTERN.matcher(input).matches();
  }

  public enum TokenFragment {
    HEADER,
    BODY,
    SIGNATURE
  }
}
