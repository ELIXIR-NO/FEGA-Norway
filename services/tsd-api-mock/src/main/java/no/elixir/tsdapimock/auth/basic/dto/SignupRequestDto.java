package no.elixir.tsdapimock.auth.basic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequestDto(
    @NotBlank(message = "Client name can not be blank") @JsonProperty("client_name")
        String clientName,
    @NotBlank(message = "email can not be blank") @Email(message = "Email must be valid") @JsonProperty("EMAIL")
        String email) {}
