package no.elixir.tsdapimock.endpoints.auth.basic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ConfirmRequestDto(
    @NotBlank(message = "ID must be provide") @JsonProperty("client_id") String clientId,
    @NotBlank(message = "Confirmation token must be provided") @JsonProperty("confirmation_token")
        String confirmationToken) {}
