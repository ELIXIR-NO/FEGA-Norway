package no.elixir.tsdapimock.auth.elixir;

import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import no.elixir.tsdapimock.auth.elixir.dto.ElixirTokenRequestDto;
import no.elixir.tsdapimock.exceptions.CredentialsMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/{project}/auth/elixir")
public class ElixirAuthController {
  private final ElixirAuthService elixirAuthService;

  @Autowired
  public ElixirAuthController(ElixirAuthService elixirAuthService) {
    this.elixirAuthService = elixirAuthService;
  }

  @PostMapping(
      value = "/token",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getToken(
      @PathVariable String project,
      @RequestHeader(value = "Authorization") String authorizationHeader,
      @Valid @RequestBody ElixirTokenRequestDto request) {
    try {
      var response = elixirAuthService.getToken(project, authorizationHeader, request);
      return ResponseEntity.ok(response);
    } catch (CredentialsMismatchException e) {
      log.error(e.getMessage());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (IllegalArgumentException | JwtException e) {
      log.error(e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (Exception e) {
      log.error(e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }
}
