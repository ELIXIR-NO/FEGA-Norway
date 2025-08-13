package no.elixir.tsdapimock.endpoints.auth.basic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SignupConfirmResponseDto(
    @JsonProperty("confirmation_token") String confirmationToken) {}
