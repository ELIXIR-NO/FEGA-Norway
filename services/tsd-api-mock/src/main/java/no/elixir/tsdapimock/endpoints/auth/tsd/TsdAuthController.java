package no.elixir.tsdapimock.endpoints.auth.tsd;

import jakarta.validation.Valid;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.endpoints.auth.tsd.dto.TsdTokenRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/{project}/auth/tsd")
public class TsdAuthController {
  private final TsdAuthService tsdAuthService;

  @Autowired
  public TsdAuthController(TsdAuthService tsdAuthService) {
    this.tsdAuthService = tsdAuthService;
  }

  @PostMapping(
      value = "/token",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getToken(
      @PathVariable String project,
      @RequestHeader(value = "Authorization") String authorizationHeader,
      @Valid @RequestBody TsdTokenRequestDto request) {
    try {
      var response = tsdAuthService.getToken(project, authorizationHeader, request);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }
  }
}
