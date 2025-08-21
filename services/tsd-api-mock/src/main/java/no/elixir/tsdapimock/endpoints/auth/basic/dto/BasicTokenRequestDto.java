package no.elixir.tsdapimock.endpoints.auth.basic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record BasicTokenRequestDto(
    @JsonProperty("type") @NotBlank(message = "Type can not be blank") String type) {}
