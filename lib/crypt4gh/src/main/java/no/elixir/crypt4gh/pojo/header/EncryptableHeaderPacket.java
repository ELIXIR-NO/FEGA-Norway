package no.elixir.crypt4gh.pojo.header;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import lombok.Data;
import lombok.ToString;
import no.elixir.crypt4gh.pojo.Crypt4GHEntity;

/** Header packet that can be encrypted, bears packet type. */
@ToString
@Data
public abstract class EncryptableHeaderPacket implements Crypt4GHEntity {

  /** The type of the header packet; either Data Encryption Parameters or Data Edit List */
  protected HeaderPacketType packetType;

  /**
   * Reads an encryptable header packet from an input stream.
   *
   * <p>The returned packet can either be a Data Encryption Parameters packet (containing the
   * symmetric key used to encrypt the actual data) or a Data Edit List
   *
   * @param inputStream the stream to read the packet data from
   * @return an encryptable header packet
   * @throws IOException if the packet data cannot be read from the stream
   * @throws GeneralSecurityException if the type of the header packet cannot be determined from the
   *     stream
   */
  static EncryptableHeaderPacket create(InputStream inputStream)
      throws IOException, GeneralSecurityException {
    int headerPacketTypeCode = Crypt4GHEntity.getInt(inputStream.readNBytes(4));
    HeaderPacketType headerPacketType = HeaderPacketType.getByCode(headerPacketTypeCode);
    return switch (headerPacketType) {
      case DATA_ENCRYPTION_PARAMETERS -> DataEncryptionParameters.create(inputStream);
      case DATA_EDIT_LIST -> new DataEditList(inputStream);
    };
  }
}
