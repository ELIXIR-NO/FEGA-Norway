package no.elixir.fega.ltp.errors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import no.elixir.fega.ltp.exceptions.JsonSchemaValidationException;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every failure to an RFC 9457 problem with a {@link ErrorCode} and the request id, see
 * ELIXIR-NO/FEGA-Norway#913. Spring MVC's own exceptions keep their statuses through the base
 * class; only the code and request id are added to them.
 */
@Slf4j
@RestControllerAdvice
public class ProblemAdvice extends ResponseEntityExceptionHandler {

  private static final String CODE = "code";

  @ExceptionHandler(ProxyException.class)
  public ResponseEntity<ProblemDetail> handleProxyException(
      ProxyException e, HttpServletRequest request, HttpServletResponse response) throws Exception {
    return problem(e.getCode(), e, request, response);
  }

  /** The client hung up mid-upload or mid-download: nobody is left to answer. */
  @ExceptionHandler(ClientAbortException.class)
  public ResponseEntity<ProblemDetail> handleClientAbort(
      ClientAbortException e, HttpServletRequest request) {
    log.warn("Client aborted [{}]: {}", RequestIdFilter.requestId(request), e.getMessage());
    return null;
  }

  /** Apart from a client abort, the only I/O a controller does is its calls to TSD. */
  @ExceptionHandler(IOException.class)
  public ResponseEntity<ProblemDetail> handleIOException(
      IOException e, HttpServletRequest request, HttpServletResponse response) throws Exception {
    return problem(ErrorCode.UPSTREAM_FAILED, e, request, response);
  }

  @ExceptionHandler(AmqpException.class)
  public ResponseEntity<ProblemDetail> handleAmqpException(
      AmqpException e, HttpServletRequest request, HttpServletResponse response) throws Exception {
    return problem(ErrorCode.UPSTREAM_FAILED, e, request, response);
  }

  @ExceptionHandler(JsonSchemaValidationException.class)
  public ResponseEntity<ProblemDetail> handleSchemaValidation(
      JsonSchemaValidationException e, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    return problem(ErrorCode.INVALID_REQUEST, e, request, response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(
      Exception e, HttpServletRequest request, HttpServletResponse response) throws Exception {
    return problem(ErrorCode.INTERNAL, e, request, response);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ProblemDetail problem =
        body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(statusCode);
    ErrorCode code = statusCode.is5xxServerError() ? ErrorCode.INTERNAL : ErrorCode.INVALID_REQUEST;
    HttpServletRequest servletRequest =
        ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
    String requestId = RequestIdFilter.requestId(servletRequest);
    log.warn("{} [{}] {}: {}", code, requestId, statusCode.value(), ex.getMessage());
    problem.setDetail(code.getDetail());
    problem.setProperty(CODE, code.name());
    problem.setProperty(RequestIdFilter.REQUEST_ID, requestId);
    return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
  }

  private ResponseEntity<ProblemDetail> problem(
      ErrorCode code, Exception e, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    String requestId = RequestIdFilter.requestId(request);
    if (code.getStatus().is5xxServerError()) {
      log.error("{} [{}]: {}", code, requestId, e.getMessage(), e);
    } else {
      log.warn("{} [{}]: {}", code, requestId, e.getMessage(), e);
    }
    if (response.isCommitted()) {
      // A download that fails mid-copy has already sent its 200. Rethrowing leaves the exception
      // to the container, which drops the connection instead of ending the chunked body cleanly,
      // so the client sees a broken transfer rather than a complete-looking file.
      throw e;
    }
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), code.getDetail());
    problem.setTitle(code.getTitle());
    problem.setProperty(CODE, code.name());
    problem.setProperty(RequestIdFilter.REQUEST_ID, requestId);
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.getStatus());
    // RFC 6750 challenge only where the token travels as a bearer credential; /export/gdi carries
    // it in the JSON body behind a Basic login.
    if (code == ErrorCode.ELIXIR_AAI_TOKEN_INVALID
        && request.getHeader(HttpHeaders.PROXY_AUTHORIZATION) != null) {
      builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
    }
    return builder.body(problem);
  }
}
