package no.elixir.tsdapimock.egaout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Record representing a TSD file with all metadata */
public record TSDFileDto(
    @JsonProperty("filename") String fileName,
    @JsonProperty("size") Long size,
    @JsonProperty("modified_date") String modifiedDate,
    @JsonProperty("href") String href,
    @JsonProperty("exportable") Boolean exportable,
    @JsonProperty("reason") String reason,
    @JsonProperty("mime-type") String mimeType,
    @JsonProperty("owner") String owner) {}
