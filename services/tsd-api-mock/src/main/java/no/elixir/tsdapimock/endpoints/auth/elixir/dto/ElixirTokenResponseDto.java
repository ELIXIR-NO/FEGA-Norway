package no.elixir.tsdapimock.endpoints.auth.elixir.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ElixirTokenResponseDto(@JsonProperty("token") String token) {}
