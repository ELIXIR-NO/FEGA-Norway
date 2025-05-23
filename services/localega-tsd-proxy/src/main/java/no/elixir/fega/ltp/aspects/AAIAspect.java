package no.elixir.fega.ltp.aspects;

import static no.elixir.fega.ltp.aspects.ProcessArgumentsAspect.ELIXIR_ID;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import no.elixir.clearinghouse.model.Visa;
import no.elixir.fega.ltp.authentication.CEGACredentialsProvider;
import no.elixir.fega.ltp.dto.Credentials;
import no.elixir.fega.ltp.services.TokenService;
import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AuthenticationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/** AOP aspect that handles authentication and authorization. */
@Slf4j
@Aspect
@Order(1)
@Component
public class AAIAspect {

  protected HttpServletRequest request;
  protected CEGACredentialsProvider cegaCredentialsProvider;
  protected TokenService tokenService;

  private final String elixirAAIClientId;

  @Autowired
  public AAIAspect(
      HttpServletRequest request,
      CEGACredentialsProvider cegaCredentialsProvider,
      TokenService tokenService,
      @Value("${elixir.client.id}") String elixirAAIClientId) {
    this.request = request;
    this.cegaCredentialsProvider = cegaCredentialsProvider;
    this.tokenService = tokenService;
    this.elixirAAIClientId = elixirAAIClientId;
  }

  /**
   * Checks GA4GH Visas. Decides on whether to allow the request or not.
   *
   * @param joinPoint Join point referencing proxied method.
   * @return Either the object, returned by the proxied method, or HTTP error response.
   * @throws Throwable In case of error.
   */
  @Around("execution(public * no.elixir.fega.ltp.controllers.rest.ProxyController.*(..))")
  public Object authenticateElixirAAI(ProceedingJoinPoint joinPoint) throws Throwable {
    Optional<String> optionalBearerAuth = getBearerAuth();
    if (optionalBearerAuth.isEmpty()) {
      log.info("Authentication attempt without Elixir AAI token provided");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String passportScopedAccessToken = optionalBearerAuth.get().replace("Bearer ", "");
    try {
      String audience = tokenService.getAudience(passportScopedAccessToken);
      if (!elixirAAIClientId.equals(audience))
        throw new AuthenticationException(
            String.format(
                "Incorrect JWT audience! Expected '%s' but got '%s'", elixirAAIClientId, audience));
      String subject = tokenService.getSubject(passportScopedAccessToken);
      List<Visa> controlledAccessGrantsVisas =
          tokenService.getControlledAccessGrantsVisas(passportScopedAccessToken);
      log.info(
          "Elixir user {} authenticated and provided following valid GA4GH Visas: {}",
          subject,
          controlledAccessGrantsVisas);
      request.setAttribute(ELIXIR_ID, subject);
      return joinPoint.proceed();
    } catch (Exception e) {
      log.info(e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
  }

  /**
   * Checks CEGA credentials. Decides on whether to allow the request or not.
   *
   * @param joinPoint Join point referencing proxied method.
   * @return Either the object, returned by the proxied method, or HTTP error response.
   * @throws Throwable In case of error.
   */
  @Around(
      "execution(public * no.elixir.fega.ltp.controllers.rest.ProxyController.*(..)) && "
          + "!execution(public * no.elixir.fega.ltp.controllers.rest.ProxyController.stream(jakarta.servlet.http.HttpServletResponse, String, String))")
  // we don't need CEGA auth for Data Out endpoints
  public Object authenticateCEGA(ProceedingJoinPoint joinPoint) throws Throwable {
    if (((MethodSignature) joinPoint.getSignature())
        .getMethod()
        .getName()
        .equalsIgnoreCase("getFiles")) {
      if (Boolean.FALSE.equals(joinPoint.getArgs()[1])) {
        return joinPoint.proceed(); // skip it for listing files in the outbox
      }
    }
    Optional<String> optionalBasicAuth = getBasicAuth();
    if (optionalBasicAuth.isEmpty()) {
      log.info("Authentication attempt without EGA credentials provided");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      String[] usernameAndPassword =
          new String(Base64.getDecoder().decode(optionalBasicAuth.get().replace("Basic ", "")))
              .split(":");
      if (!cegaAuth(usernameAndPassword[0], usernameAndPassword[1])) {
        throw new AuthenticationException("EGA authentication failed");
      }
      log.info("EGA user {} authenticated", usernameAndPassword[0]);
      request.setAttribute(ProcessArgumentsAspect.EGA_USERNAME, usernameAndPassword[0]);
      return joinPoint.proceed();
    } catch (Exception e) {
      log.info(e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
  }

  protected boolean cegaAuth(String username, String password)
      throws MalformedURLException, URISyntaxException {
    Credentials credentials = cegaCredentialsProvider.getCredentials(username);
    String hash = credentials.getPasswordHash();
    return StringUtils.startsWithIgnoreCase(hash, "$2")
        ? BCrypt.checkpw(password, hash)
        : ObjectUtils.nullSafeEquals(hash, Crypt.crypt(password, hash));
  }

  protected Optional<String> getBasicAuth() {
    return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION));
  }

  protected Optional<String> getBearerAuth() {
    return Optional.ofNullable(request.getHeader(HttpHeaders.PROXY_AUTHORIZATION));
  }
}
