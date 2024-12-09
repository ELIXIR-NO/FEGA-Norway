package no.elixir.fega.ltp.exceptions;

import java.io.Serial;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GenericException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;
  private HttpStatus httpStatus;
  private String message;
}
