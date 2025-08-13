package no.elixir.tsdapimock.core.validation;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ValidationExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationExceptions(
      MethodArgumentNotValidException e) {
    ErrorResponse error = new ErrorResponse();
    error.setTimestamp(LocalDateTime.now());
    error.setMessage("Validation Error");
    error.setDetails(
        e.getBindingResult().getAllErrors().stream()
            .map(ObjectError::getDefaultMessage)
            .collect(Collectors.toList()));
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(
      MissingRequestHeaderException e) {
    ErrorResponse error = new ErrorResponse();
    error.setTimestamp(LocalDateTime.now());
    error.setMessage("Missing Request Header Error");
    error.setDetails(
        Collections.singletonList("Required header '" + e.getHeaderName() + "' is missing."));
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }
}
