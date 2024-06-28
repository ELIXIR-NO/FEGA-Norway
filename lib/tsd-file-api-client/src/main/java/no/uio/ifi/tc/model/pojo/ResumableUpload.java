package no.uio.ifi.tc.model.pojo;

import com.google.gson.annotations.SerializedName;
import java.math.BigInteger;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Data
public class ResumableUpload extends TSDFileAPIResponse {

  @SerializedName("id")
  private String id;

  @SerializedName("filename")
  private String fileName;

  @SerializedName("group")
  private String group;

  @SerializedName("chunk_size")
  private BigInteger chunkSize;

  @SerializedName("previous_offset")
  private BigInteger previousOffset;

  @SerializedName("next_offset")
  private BigInteger nextOffset;

  @SerializedName("max_chunk")
  private BigInteger maxChunk;

  @SerializedName("md5Sum")
  private String md5Sum;
}
