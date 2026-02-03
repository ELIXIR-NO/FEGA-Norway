package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import no.elixir.fega.ltp.common.Masker;

/** POJO for MQ message to publish. */
@ToString
@Data
public class FileDescriptor {

  @JsonProperty("user")
  @ToString.Exclude
  private String user;

  @JsonIgnore
  @ToString.Include(name = "user")
  private String maskUser() {
    return Masker.maskUsername(user);
  }

  @JsonProperty("filepath")
  @ToString.Exclude
  private String filePath;

  @JsonIgnore
  @ToString.Include(name = "filepath")
  private String maskFilepath() {
    return Masker.maskEmailInPath(filePath);
  }

  @JsonProperty("operation")
  private String operation;

  @JsonProperty("filesize")
  private Long fileSize;

  @JsonProperty("oldpath")
  private String oldPath;

  @JsonProperty("file_last_modified")
  private Long fileLastModified;

  @JsonProperty("content")
  private String content;

  @JsonProperty("encrypted_checksums")
  private EncryptedIntegrity[] encryptedIntegrity;
}
