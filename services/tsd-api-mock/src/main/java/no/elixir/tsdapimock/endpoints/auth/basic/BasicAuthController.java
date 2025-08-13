package no.elixir.tsdapimock.endpoints.auth.basic;

import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import no.elixir.tsdapimock.auth.basic.dto.*;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.endpoints.auth.basic.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/{project}/auth/basic")
public class BasicAuthController {

  private final BasicAuthService basicAuthService;

  @Autowired
  public BasicAuthController(BasicAuthService basicAuthService) {
    this.basicAuthService = basicAuthService;
  }

  @PostMapping(
      value = "/signup",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> signup(
      @PathVariable String project, @Valid @RequestBody SignupRequestDto request) {
    var response = basicAuthService.signup(project, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping(
      value = "/signupconfirm",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> signupConfirm(
      @PathVariable String project, @Valid @RequestBody SignupConfirmRequestDto request) {
    try {
      var response = basicAuthService.signupConfirm(project, request);
      return ResponseEntity.ok(response);
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
  }

  @PostMapping(
      value = "/confirm",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> confirm(
      @PathVariable String project, @Valid @RequestBody ConfirmRequestDto request) {
    try {
      var response = basicAuthService.confirm(project, request);
      return ResponseEntity.ok(response);
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }
  }

  @PostMapping(
      value = "/api_key",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getApiKey(
      @PathVariable String project, @Valid @RequestBody ApiKeyRequestDto request) {
    try {
      var apiKey = basicAuthService.getApiKey(project, request);
      return ResponseEntity.ok(apiKey);
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
  }

  @PostMapping(
      value = "/token",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getToken(
      @PathVariable String project,
      @RequestHeader(value = "Authorization") String authorizationHeader,
      @Valid @RequestBody BasicTokenRequestDto request) {
    try {
      var response = basicAuthService.getToken(project, authorizationHeader, request);
      return ResponseEntity.ok(response);
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }
  }
}
