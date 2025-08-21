package no.elixir.tsdapimock.endpoints.auth.tsd.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TsdTokenResponseDto(@JsonProperty("token") String token) {}
