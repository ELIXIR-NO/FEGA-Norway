package no.elixir.tsdapimock.endpoints.egaout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;

/** Record representing the file listing response wrapper */
public record OutboxFileListingDto(
    @JsonProperty("files") Collection<TSDFileDto> files, @JsonProperty("page") String page) {}
