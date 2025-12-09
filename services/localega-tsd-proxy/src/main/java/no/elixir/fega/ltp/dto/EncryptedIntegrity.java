package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

/** Nested POJO for MQ message to publish. */
@ToString
@Data
public class EncryptedIntegrity {

  @JsonProperty("type")
  private final String algorithm;

  @JsonProperty("value")
  private final String checksum;
}
