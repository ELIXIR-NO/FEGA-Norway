package no.elixir.tsdapimock.core.resumables;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigInteger;
import lombok.Data;

@Data
@Entity
public class ResumableUpload {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  private String fileName;

  private String memberGroup;

  private BigInteger chunkSize;

  private BigInteger previousOffset;

  private BigInteger nextOffset;

  private BigInteger maxChunk;

  private String md5Sum;
}
