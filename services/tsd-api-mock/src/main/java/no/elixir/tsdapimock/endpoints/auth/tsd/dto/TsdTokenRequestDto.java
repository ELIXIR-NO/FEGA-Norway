package no.elixir.tsdapimock.endpoints.auth.tsd.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record TsdTokenRequestDto(
    @JsonProperty("user_name") @NotBlank(message = "User name can not blank") String userName,
    @JsonProperty("otp") @NotBlank(message = "OTP must not be blank") String opt,
    @JsonProperty("password") @NotBlank(message = "Password must not be blank") String password) {}
