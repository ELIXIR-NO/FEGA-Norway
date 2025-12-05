package no.elixir.fega.ltp.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.ToString;
import no.elixir.fega.ltp.common.Masker;

/** POJO for MQ message to publish. */
@ToString
@Data
public class FileDescriptor {

  @SerializedName("user")
  @ToString.Exclude
  private String user;

  @ToString.Include(name = "user")
  private String maskUser() {
    return Masker.maskUsername(user);
  }

  @SerializedName("filepath")
  @ToString.Exclude
  private String filePath;

  @ToString.Include(name = "filepath")
  private String maskFilepath() {
    return Masker.maskEmailInPath(filePath);
  }

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
