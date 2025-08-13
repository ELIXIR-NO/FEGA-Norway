package no.elixir.tsdapimock.endpoints.auth.elixir;

import static javax.management.timer.Timer.ONE_HOUR;

import lombok.extern.slf4j.Slf4j;
import no.elixir.tsdapimock.endpoints.auth.elixir.dto.ElixirTokenRequestDto;
import no.elixir.tsdapimock.endpoints.auth.elixir.dto.ElixirTokenResponseDto;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.core.utils.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ElixirAuthService {

  private final JwtService jwtService;

  @Autowired
  public ElixirAuthService(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  public ElixirTokenResponseDto getToken(
      String project, String authorizationHeader, ElixirTokenRequestDto request) {
    if (!authorizationHeader.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Invalid authorization header");
    }
    if (!jwtService.verify(authorizationHeader)) {
      throw new CredentialsMismatchException("Invalid authorization token");
    }
    var userName = jwtService.getElixirAAITokenSubject(request.token());
    log.info("Elixir AAI username: {}", userName);
    var token = jwtService.createJwt(project, userName, "TSD", userName, ONE_HOUR);
    return new ElixirTokenResponseDto(token);
  }
}
