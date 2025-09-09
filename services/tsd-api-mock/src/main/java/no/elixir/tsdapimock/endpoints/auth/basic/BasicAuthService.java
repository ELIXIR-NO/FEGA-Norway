package no.elixir.tsdapimock.endpoints.auth.basic;

import static javax.management.timer.Timer.ONE_HOUR;
import static javax.management.timer.Timer.ONE_WEEK;

import java.security.SecureRandom;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.core.utils.JwtService;
import no.elixir.tsdapimock.endpoints.auth.basic.dto.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BasicAuthService {

  private final ClientRepository clientRepository;
  private final JwtService jwtService;

  @Autowired
  public BasicAuthService(ClientRepository clientRepository, JwtService jwtService) {
    this.clientRepository = clientRepository;
    this.jwtService = jwtService;
  }

  public SignupResponseDto signup(String project, SignupRequestDto request) {
    var confirmationToken =
        jwtService.createJwt(project, request.email(), "TSD", request.email(), ONE_HOUR);
    var client =
        Client.builder()
            .name(request.clientName())
            .email(request.email())
            .userName(request.email())
            .confirmationToken(confirmationToken)
            .build();

    var savedClient = clientRepository.save(client);

    return new SignupResponseDto(savedClient.getId());
  }

  public SignupConfirmResponseDto signupConfirm(String project, SignupConfirmRequestDto request) {
    var client = clientRepository.findById(request.clientId()).orElseThrow();

    if (!client.getEmail().equals(request.email())) {
      throw new CredentialsMismatchException("Incorrect email ID provided");
    }

    if (!client.getName().equals(request.clientName())) {
      throw new CredentialsMismatchException("Incorrect client name");
    }

    return new SignupConfirmResponseDto(client.getConfirmationToken());
  }

  public ConfirmResponseDto confirm(String project, ConfirmRequestDto request) {
    var client = clientRepository.findById(request.clientId()).orElseThrow();
    if (!client.getConfirmationToken().equals(request.confirmationToken())) {
      throw new CredentialsMismatchException("Invalid confirmation token");
    }
    char[] possibleCharacters =
        ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[{]}|;:,<.>/?")
            .toCharArray();
    int passwordLength = 12;
    String password =
        RandomStringUtils.random(
            passwordLength,
            0,
            possibleCharacters.length - 1,
            false,
            false,
            possibleCharacters,
            new SecureRandom());

    client.setPassword(password);
    clientRepository.save(client);
    return new ConfirmResponseDto(password);
  }

  public ApiKeyResponseDto getApiKey(String project, ApiKeyRequestDto request) {
    var client =
        clientRepository
            .findClientByIdAndPassword(request.clientId(), request.password())
            .orElseThrow();

    return new ApiKeyResponseDto(
        jwtService.createJwt(
            project, client.getUserName(), "TSD", client.getUserName(), ONE_WEEK * 52));
  }

  public BasicTokenResponseDto getToken(
      String project, String authorizationHeader, BasicTokenRequestDto request) {
    if (!authorizationHeader.startsWith("Bearer ")) {
      throw new CredentialsMismatchException("Authorization header my be a bearer token");
    }
    var token = jwtService.createJwt(project, "user", "TSD", "user", ONE_HOUR);
    return new BasicTokenResponseDto(token);
  }
}
