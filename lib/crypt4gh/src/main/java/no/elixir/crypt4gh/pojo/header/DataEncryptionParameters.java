package no.elixir.crypt4gh.pojo.header;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import no.elixir.crypt4gh.pojo.Crypt4GHEntity;

/** Data Encryption Parameters, bears Data Encryption Method. */
@EqualsAndHashCode(callSuper = true)
@ToString
@Data
public abstract class DataEncryptionParameters extends EncryptableHeaderPacket {

  /** The encryption method used to encrypt the payload of this header packet. */
  protected DataEncryptionMethod dataEncryptionMethod;

  /**
   * Reads a Data Encryption Parameters header packet from an input stream. The packet type field of
   * the header packet should already have been read from the stream, so the next field in the
   * stream should be the encryption method.
   *
   * @param inputStream a stream to read the header packet from
   * @return a DataEncryptionParameters object
   * @throws IOException if something goes wrong while reading from the stream
   * @throws GeneralSecurityException if the data encryption method specified in the stream is not
   *     be recognized
   */
  public static DataEncryptionParameters create(InputStream inputStream)
      throws IOException, GeneralSecurityException {
    int dataEncryptionMethodCode = Crypt4GHEntity.getInt(inputStream.readNBytes(4));
    DataEncryptionMethod dataEncryptionMethod =
        DataEncryptionMethod.getByCode(dataEncryptionMethodCode);
    switch (dataEncryptionMethod) {
      case CHACHA20_IETF_POLY1305:
        return new ChaCha20IETFPoly1305EncryptionParameters(inputStream);
      default:
        throw new GeneralSecurityException(
            "Data Encryption Method not found for code: " + dataEncryptionMethodCode);
    }
  }
}
