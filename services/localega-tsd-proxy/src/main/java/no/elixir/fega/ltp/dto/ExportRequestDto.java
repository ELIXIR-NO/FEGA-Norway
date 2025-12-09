package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ExportRequestDto {

  // This is the user's Passport Scoped Access Token.
  @NotBlank(message = "The field 'accessToken' must not be blank.")
  @JsonProperty("accessToken")
  private String accessToken;

  // This is the visa token that DOA expects
  @JsonProperty("jwtToken")
  private String jwtToken;

  @NotBlank(message = "The field 'id' must not be blank.")
  @JsonProperty("id")
  private String id;

  @NotBlank(message = "The field 'userPublicKey' must not be blank.")
  @JsonProperty("userPublicKey")
  private String userPublicKey;

  @NotNull(message = "The field 'type' must not be null. Should be either 'fileId' or 'datasetId'.")
  @JsonProperty("type")
  private ExportType type = ExportType.DATASET_ID;

  @ToString
  @AllArgsConstructor
  public enum ExportType {
    FILE_ID("fileId"),
    DATASET_ID("datasetId");
    @JsonValue
    private final String value;
    @JsonCreator
    public static ExportType fromValue(String value) {
      for (ExportType type : ExportType.values()) {
        if (type.value.equals(value)) {
          return type;
        }
      }
      throw new IllegalArgumentException("Unknown ExportType: " + value);
    }
  }
}
