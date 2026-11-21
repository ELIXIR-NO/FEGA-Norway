package no.elixir.fega.ltp.errors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id, ahead of the security chains so their failures carry it too. It goes
 * into the MDC for the log lines and into every problem body, so a user's report matches the log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID = "requestId";

  /** Returns the request's id, or a fresh one if the request never passed this filter. */
  public static String requestId(HttpServletRequest request) {
    Object id = request.getAttribute(REQUEST_ID);
    if (id == null) {
      id = UUID.randomUUID().toString();
      request.setAttribute(REQUEST_ID, id);
    }
    return id.toString();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    MDC.put(REQUEST_ID, requestId(request));
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID);
    }
  }
}
