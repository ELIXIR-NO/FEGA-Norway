package no.elixir.tsdapimock.endpoints.auth.basic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiKeyResponseDto(@JsonProperty("api_key") String apiKey) {}
