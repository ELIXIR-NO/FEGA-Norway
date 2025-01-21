package no.elixir.tsdapimock.auth.basic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record SignupConfirmRequestDto(
    @JsonProperty("client_name") @NotBlank(message = "Client name cannot be blank") String clientName,
    @JsonProperty("EMAIL") @NotBlank(message = "Email cannot be blank") String email,
    @JsonProperty("client_id") @NotBlank(message = "Client ID cannot be blank") String clientId) {}
