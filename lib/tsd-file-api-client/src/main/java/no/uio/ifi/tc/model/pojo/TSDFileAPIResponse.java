package no.uio.ifi.tc.model.pojo;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class TSDFileAPIResponse {

  @SerializedName("statusCode")
  private int statusCode;

  @SerializedName("statusText")
  private String statusText;
}
