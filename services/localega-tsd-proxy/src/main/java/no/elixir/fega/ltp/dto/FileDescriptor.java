package no.elixir.fega.ltp.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.ToString;

/** POJO for MQ message to publish. */
@ToString
@Data
public class FileDescriptor {

  @SerializedName("user")
  private String user;

  @SerializedName("filepath")
  private String filePath;

  @SerializedName("operation")
  private String operation;

  @SerializedName("filesize")
  private Long fileSize;

  @SerializedName("oldpath")
  private String oldPath;

  @SerializedName("file_last_modified")
  private Long fileLastModified;

  @SerializedName("content")
  private String content;

  @SerializedName("encrypted_checksums")
  private EncryptedIntegrity[] encryptedIntegrity;
}
