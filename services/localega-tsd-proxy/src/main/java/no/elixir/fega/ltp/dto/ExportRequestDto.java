package no.elixir.fega.ltp.dto;

import com.google.gson.annotations.SerializedName;
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
  @NotBlank(message = "The field 'accessToken' must not be blank.") @SerializedName("accessToken")
  private String accessToken;

  // This is the visa token that DOA expects
  @SerializedName("jwtToken")
  private String jwtToken;

  @NotBlank(message = "The field 'id' must not be blank.") @SerializedName("id")
  private String id;

  @NotBlank(message = "The field 'userPublicKey' must not be blank.") @SerializedName("userPublicKey")
  private String userPublicKey;

  @NotNull(message = "The field 'type' must not be null. Should be either 'fileId' or 'datasetId'.") @SerializedName("type")
  private ExportType type = ExportType.DATASET_ID;

  @ToString
  @AllArgsConstructor
  public enum ExportType {
    FILE_ID("fileId"),
    DATASET_ID("datasetId");
    private final String value;
  }
}
