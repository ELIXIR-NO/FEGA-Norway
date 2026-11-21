package no.elixir.fega.ltp.errors;

import java.io.Serial;
import lombok.Getter;

/**
 * A failure with a known {@link ErrorCode}. The message and cause go to the server log only; the
 * client sees the code's fixed detail.
 */
@Getter
public class ProxyException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public ProxyException(ErrorCode code, String logMessage) {
    super(logMessage);
    this.code = code;
  }

  public ProxyException(ErrorCode code, String logMessage, Throwable cause) {
    super(logMessage, cause);
    this.code = code;
  }
}
