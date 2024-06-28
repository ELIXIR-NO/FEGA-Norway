package no.elixir.tsdapimock.resumables;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;

public record ResumableUploadDto(
    @JsonProperty("id") String id,
    @JsonProperty("filename") String fileName,
    @JsonProperty("group") String memberGroup,
    @JsonProperty("chunk_size") BigInteger chunkSize,
    @JsonProperty("previous_offset") BigInteger previousOffset,
    @JsonProperty("next_offset") BigInteger nextOffset,
    @JsonProperty("max_chunk") BigInteger maxChunk,
    @JsonProperty("md5Sum") String md5Sum) {}
