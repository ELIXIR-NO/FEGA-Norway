package no.elixir.fega.ltp.aspects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import no.elixir.fega.ltp.authentication.CEGACredentialsProvider;
import no.elixir.fega.ltp.services.TokenService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AAIAspectTest {

  private HttpServletRequest request;
  private TokenService tokenService;
  private ProceedingJoinPoint joinPoint;
  private AAIAspect aspect;

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    tokenService = mock(TokenService.class);
    joinPoint = mock(ProceedingJoinPoint.class);
    aspect =
        new AAIAspect(request, mock(CEGACredentialsProvider.class), tokenService, "test-client-id");
  }

  /**
   * Audit control C1, response half: when token verification refuses a token, the request must end
   * as 403 and the proxied call must never run. Guards the catch block that turns a verifier
   * exception into a response; a refactor that swallows the exception and proceeds goes red here.
   */
  @Test
  void authenticateElixirAAI_returns403WithoutProceeding_whenVerificationFails() throws Throwable {
    when(request.getHeader(HttpHeaders.PROXY_AUTHORIZATION)).thenReturn("Bearer forged-token");
    when(tokenService.parseVerified(anyString()))
        .thenThrow(new SignatureException("JWT signature does not match"));

    Object response = aspect.authenticateElixirAAI(joinPoint);

    assertThat(response).isInstanceOf(ResponseEntity.class);
    assertThat(((ResponseEntity<?>) response).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verify(tokenService).parseVerified("forged-token");
    verify(joinPoint, never()).proceed();
  }

  @Test
  void authenticateElixirAAI_returns401WithoutProceeding_whenTokenMissing() throws Throwable {
    when(request.getHeader(HttpHeaders.PROXY_AUTHORIZATION)).thenReturn(null);

    Object response = aspect.authenticateElixirAAI(joinPoint);

    assertThat(response).isInstanceOf(ResponseEntity.class);
    assertThat(((ResponseEntity<?>) response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verify(joinPoint, never()).proceed();
  }
}
