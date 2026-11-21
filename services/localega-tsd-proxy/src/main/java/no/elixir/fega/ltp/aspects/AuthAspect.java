package no.elixir.fega.ltp.aspects;

import static no.elixir.fega.ltp.aspects.ProcessArgumentsAspect.EGA_USERNAME;
import static no.elixir.fega.ltp.aspects.ProcessArgumentsAspect.ELIXIR_ID;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import no.elixir.clearinghouse.model.Visa;
import no.elixir.fega.ltp.authentication.CEGACredentialsProvider;
import no.elixir.fega.ltp.common.Masker;
import no.elixir.fega.ltp.dto.Credentials;
import no.elixir.fega.ltp.errors.ErrorCode;
import no.elixir.fega.ltp.errors.ProxyException;
import no.elixir.fega.ltp.services.TokenService;
import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Authenticates every {@code ProxyController} call: the LS AAI token from {@code
 * Proxy-Authorization} first, then the EGA credentials from {@code Authorization} unless the
 * endpoint carries {@link NoEgaAuth}. A failure throws a {@link ProxyException}, which the problem
 * advice answers.
 */
@Slf4j
@Aspect
@Order(1)
@Component
public class AuthAspect {

  /** Request attribute holding the bare LS AAI token, for the controller's TSD token exchange. */
  public static final String ELIXIR_AAI_TOKEN = "elixirAAIToken";

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String BASIC_PREFIX = "Basic ";

  // Checked against on an unknown user so the answer takes about as long as a wrong password.
  // That holds while CEGA's hashes are bcrypt at cost 10, gensalt's default, as they are today.
  private static final String DUMMY_HASH = BCrypt.hashpw("unknown-user", BCrypt.gensalt());

  private final HttpServletRequest request;
  private final CEGACredentialsProvider cegaCredentialsProvider;
  private final TokenService tokenService;
  private final String elixirAAIClientId;

  @Autowired
  public AuthAspect(
      HttpServletRequest request,
      CEGACredentialsProvider cegaCredentialsProvider,
      TokenService tokenService,
      @Value("${elixir.client.id}") String elixirAAIClientId) {
    this.request = request;
    this.cegaCredentialsProvider = cegaCredentialsProvider;
    this.tokenService = tokenService;
    this.elixirAAIClientId = elixirAAIClientId;
  }

  @Around("execution(public * no.elixir.fega.ltp.controllers.rest.ProxyController.*(..))")
  public Object authenticate(ProceedingJoinPoint joinPoint) throws Throwable {
    authenticateElixirAAI();
    if (!((MethodSignature) joinPoint.getSignature())
        .getMethod()
        .isAnnotationPresent(NoEgaAuth.class)) {
      authenticateEGA();
    }
    return joinPoint.proceed();
  }

  private void authenticateElixirAAI() {
    String header = request.getHeader(HttpHeaders.PROXY_AUTHORIZATION);
    if (StringUtils.isBlank(header)) {
      throw new ProxyException(ErrorCode.MISSING_CREDENTIALS, "No LS AAI token provided");
    }
    String token = StringUtils.removeStart(header, BEARER_PREFIX);

    Claims claims = tokenService.parseVerified(token);
    String audience =
        claims.getAudience() != null && !claims.getAudience().isEmpty()
            ? claims.getAudience().iterator().next()
            : null;
    if (!elixirAAIClientId.equals(audience)) {
      throw new ProxyException(
          ErrorCode.ELIXIR_AAI_TOKEN_INVALID, "LS AAI token issued for another audience");
    }
    String subject = claims.getSubject();
    if (StringUtils.isEmpty(subject)) {
      throw new ProxyException(ErrorCode.ELIXIR_AAI_TOKEN_INVALID, "LS AAI token has no subject");
    }

    List<Visa> controlledAccessGrantsVisas = tokenService.getControlledAccessGrantsVisas(token);

    log.info(
        "Elixir user {} authenticated and provided following valid GA4GH Visas: {}",
        Masker.maskEmail(subject.replaceAll("[\\r\\n]", "_")),
        controlledAccessGrantsVisas);
    request.setAttribute(ELIXIR_ID, subject);
    request.setAttribute(ELIXIR_AAI_TOKEN, token);
  }

  private void authenticateEGA() {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (!StringUtils.startsWithIgnoreCase(header, BASIC_PREFIX)) {
      throw new ProxyException(ErrorCode.MISSING_CREDENTIALS, "No EGA credentials provided");
    }
    String decoded;
    try {
      decoded =
          new String(
              Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length()).trim()),
              StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new ProxyException(ErrorCode.EGA_AUTH_FAILED, "EGA credentials are not base64", e);
    }
    String[] usernameAndPassword = decoded.split(":", 2);
    if (usernameAndPassword.length < 2) {
      throw new ProxyException(ErrorCode.EGA_AUTH_FAILED, "EGA credentials lack a password");
    }
    String username = usernameAndPassword[0];
    if (!cegaAuth(username, usernameAndPassword[1])) {
      throw new ProxyException(
          ErrorCode.EGA_AUTH_FAILED,
          "Wrong password for EGA user " + Masker.maskUsername(username));
    }
    log.info("EGA user {} authenticated", Masker.maskUsername(username));
    request.setAttribute(EGA_USERNAME, username);
  }

  private boolean cegaAuth(String username, String password) {
    Credentials credentials;
    try {
      credentials = cegaCredentialsProvider.getCredentials(username);
    } catch (HttpClientErrorException.BadRequest | URISyntaxException e) {
      // CEGA answers an unknown username with 400 "No info for that user"; a username that does
      // not even form a lookup URL is just as unknown.
      BCrypt.checkpw(password, DUMMY_HASH);
      throw new ProxyException(
          ErrorCode.EGA_AUTH_FAILED,
          "CEGA does not know EGA user " + Masker.maskUsername(username),
          e);
    } catch (MalformedURLException e) {
      throw new ProxyException(ErrorCode.INTERNAL, "cega.auth-url is not a valid URL", e);
    } catch (RestClientException e) {
      // Any other answer, a 401/403 on the proxy's own CEGA login or a 429 included, says nothing
      // about the user's password.
      throw new ProxyException(ErrorCode.CEGA_UNAVAILABLE, "CEGA lookup failed", e);
    }
    String hash = credentials.getPasswordHash();
    return StringUtils.startsWithIgnoreCase(hash, "$2")
        ? BCrypt.checkpw(password, hash)
        : ObjectUtils.nullSafeEquals(hash, Crypt.crypt(password, hash));
  }
}
