package no.uio.ifi.tc.model.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class TSDFileAPIResponse {

  @JsonProperty("statusCode")
  @SerializedName("statusCode")
  private int statusCode;

  @JsonProperty("statusText")
  @SerializedName("statusText")
  private String statusText;
}
