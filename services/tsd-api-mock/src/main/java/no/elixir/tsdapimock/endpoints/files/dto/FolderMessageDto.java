package no.elixir.tsdapimock.endpoints.files.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FolderMessageDto(@JsonProperty("message") String message) {}
