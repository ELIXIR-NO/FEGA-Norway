package no.elixir.tsdapimock.core.validation;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ErrorResponse {
  private LocalDateTime timestamp;
  private String message;
  private List<String> details;
}
