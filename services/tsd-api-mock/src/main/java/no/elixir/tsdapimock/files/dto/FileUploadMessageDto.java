package no.elixir.tsdapimock.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FileUploadMessageDto(@JsonProperty("message") String message) {}
