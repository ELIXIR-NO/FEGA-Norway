package no.elixir.tsdapimock.endpoints.auth.basic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ApiKeyRequestDto(
    @NotBlank(message = "Client ID cannot be blank") @JsonProperty("client_id") String clientId,
    @NotBlank(message = "Password cannot be blank") @JsonProperty("password") String password) {}
