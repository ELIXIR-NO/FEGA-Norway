package no.elixir.crypt4gh.pojo.header;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.ToString;
import no.elixir.crypt4gh.pojo.Crypt4GHEntity;

/** Header packet, bearing its length, encryption type and encrypted payload. */
@ToString
@Data
public abstract class HeaderPacket implements Crypt4GHEntity {

  /** The total size of this header packet */
  protected int packetLength;

  /** The encryption method used to encrypt the payload in this header packet */
  protected HeaderEncryptionMethod packetEncryption;

  /** The payload that can be encrypted and stored in this header packet */
  protected EncryptableHeaderPacket encryptablePayload;

  /**
   * Tries to read and decrypt a header packet from an input stream. If the header packet cannot be
   * decrypted with the provided private key, an empty Optional is returned instead. This can happen
   * if the header packet was encrypted for a different target recipient.
   *
   * @param inputStream the stream to read the header packet from
   * @param readerPrivateKey the private key of the reader
   * @return an Optional that may contain a header packet if it could successfully be decrypted with
   *     the provided key
   * @throws IOException if somewhing goes wrong while reading from the stream
   * @throws GeneralSecurityException if the encryption method specified in the header packet was
   *     not recognized
   */
  static Optional<HeaderPacket> create(InputStream inputStream, PrivateKey readerPrivateKey)
      throws IOException, GeneralSecurityException {
    int packetLength = Crypt4GHEntity.getInt(inputStream.readNBytes(4));
    int packetEncryptionCode = Crypt4GHEntity.getInt(inputStream.readNBytes(4));
    HeaderEncryptionMethod packetEncryption =
        HeaderEncryptionMethod.getByCode(packetEncryptionCode);
    byte[] encryptedPayload = inputStream.readNBytes(packetLength - 4 - 4);
    if (Objects.requireNonNull(packetEncryption)
        == HeaderEncryptionMethod.X25519_CHACHA20_IETF_POLY1305) {
      try {
        return Optional.of(
            new X25519ChaCha20IETFPoly1305HeaderPacket(
                packetLength, encryptedPayload, readerPrivateKey));
      } catch (GeneralSecurityException e) {
        return Optional.empty();
      }
    }
    throw new GeneralSecurityException(
        "Header Encryption Method not found for code: " + packetEncryptionCode);
  }
}
