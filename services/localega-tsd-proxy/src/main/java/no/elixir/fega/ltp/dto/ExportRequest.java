package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonObject;
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
public class ExportRequest {

  // This is the user's Passport Scoped Access Token.
  @NotBlank(message = "The field 'accessToken' must not be blank.") @JsonProperty
  private String accessToken;

  // This is the visa token that DOA expects
  @JsonIgnore private String jwtToken;

  @NotBlank(message = "The field 'id' must not be blank.") @JsonProperty
  private String id;

  @NotBlank(message = "The field 'userPublicKey' must not be blank.") @JsonProperty
  private String userPublicKey;

  @NotNull(message = "The field 'type' must not be null. Should be either 'fileId' or 'datasetId'.") @JsonProperty
  private ExportType type = ExportType.DATASET_ID;

  public String getInfoRequiredForDOAExportRequestAsJson() {
    JsonObject obj = new JsonObject();
    obj.addProperty("jwtToken", jwtToken);
    obj.addProperty(type.value, id);
    obj.addProperty("userPublicKey", userPublicKey);
    return obj.getAsString();
  }

  @ToString
  @AllArgsConstructor
  public enum ExportType {
    FILE_ID("fileId"),
    DATASET_ID("datasetId");
    private final String value;
  }
}
