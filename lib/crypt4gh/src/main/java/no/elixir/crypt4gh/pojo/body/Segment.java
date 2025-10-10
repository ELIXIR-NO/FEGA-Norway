package no.elixir.crypt4gh.pojo.body;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Optional;
import lombok.Data;
import lombok.ToString;
import no.elixir.crypt4gh.pojo.Crypt4GHEntity;
import no.elixir.crypt4gh.pojo.header.ChaCha20IETFPoly1305EncryptionParameters;
import no.elixir.crypt4gh.pojo.header.DataEncryptionMethod;
import no.elixir.crypt4gh.pojo.header.DataEncryptionParameters;

/**
 * A segment represents one 64 KB block of data in the body of the Crypt4GH file following the
 * header. A segment is 65536 bytes long unencrypted and 65564 bytes long encrypted, including nonce
 * and MAC (according to the current spec). (the final segment in a file can be shorter)
 */
@ToString
@Data
public abstract class Segment implements Crypt4GHEntity {

  /** Size of one data block not counting nonce and MAC (64 KB = 65536 bytes) */
  public static final int UNENCRYPTED_DATA_SEGMENT_SIZE = 65536;

  /**
   * The unencrypted data for this segment. (The encrypted data is stored in subclasses specific to
   * each cipher)
   */
  protected byte[] unencryptedData;

  /**
   * Creates a new Segment from a block of unencrypted data. The provided data will be encrypted
   * using the encryption method and key defined in the Data Encryption Parameters.
   *
   * @param unencryptedData the unencrypted data for this segment
   * @param dataEncryptionParameters specifying the encryption method and containing the encryption
   *     key
   * @return a Segment of a subclass depending on the encryption method defined in the Data
   *     Encryption Parameters
   * @throws GeneralSecurityException if the encryption method was not recognized or the encryption
   *     failed for other reasons
   */
  public static Segment create(
      byte[] unencryptedData, DataEncryptionParameters dataEncryptionParameters)
      throws GeneralSecurityException {
    DataEncryptionMethod dataEncryptionMethod = dataEncryptionParameters.getDataEncryptionMethod();
    switch (dataEncryptionMethod) {
      case CHACHA20_IETF_POLY1305:
        return new ChaCha20IETFPoly1305Segment(
            unencryptedData,
            (ChaCha20IETFPoly1305EncryptionParameters) dataEncryptionParameters,
            true);
      default:
        throw new GeneralSecurityException(
            "Data Encryption Method not found for code: " + dataEncryptionMethod.getCode());
    }
  }

  /**
   * Creates a new Segment from a block of encrypted data.
   *
   * <p>The method will go through the list of provided Data Encryption Parameters one by one and
   * attempt to decrypt the data using the cipher and key defined in each parameter object. An
   * exception will be thrown if the data cannot be decrypted with any of those keys.
   *
   * @param encryptedData the encrypted data for this segment
   * @param dataEncryptionParametersList a list of Data Encryption Parameters, each specifying an
   *     encryption method and containing a decryption key
   * @return a new Segment (the specific subclass will depend on the cipher that was used to
   *     successfully decrypt the data)
   * @throws GeneralSecurityException if the data block could not be decrypted with any of the
   *     provided Data Encryption Parameters
   */
  public static Segment create(
      byte[] encryptedData, Collection<DataEncryptionParameters> dataEncryptionParametersList)
      throws GeneralSecurityException {
    for (DataEncryptionParameters dataEncryptionParameters : dataEncryptionParametersList) {
      Optional<Segment> segmentOptional = tryCreate(encryptedData, dataEncryptionParameters);

      if (segmentOptional.isPresent()) {
        return segmentOptional.get();
      }
    }
    throw new GeneralSecurityException(
        "Data Segment can't be decrypted with any of the Header keys");
  }

  /**
   * Tries to decrypt a segment from a block of encrypted data using the cipher and decryption key
   * included in the Data Encryption Parameters.
   *
   * @param encryptedData the encrypted data for this segment
   * @param dataEncryptionParameters specifying the encryption method and containing the decryption
   *     key
   * @return an Optional containing the decrypted segment, or an empty Optional if the segment could
   *     not be decrypted with the provided key
   * @throws GeneralSecurityException if the encryption method specified in the Data Encryption
   *     Parameters is not recognized
   */
  private static Optional<Segment> tryCreate(
      byte[] encryptedData, DataEncryptionParameters dataEncryptionParameters)
      throws GeneralSecurityException {
    DataEncryptionMethod dataEncryptionMethod = dataEncryptionParameters.getDataEncryptionMethod();
    switch (dataEncryptionMethod) {
      case CHACHA20_IETF_POLY1305:
        try {
          return Optional.of(
              new ChaCha20IETFPoly1305Segment(
                  encryptedData,
                  (ChaCha20IETFPoly1305EncryptionParameters) dataEncryptionParameters,
                  false));
        } catch (GeneralSecurityException e) {
          return Optional.empty();
        }
      default:
        throw new GeneralSecurityException(
            "Data Encryption Method not found for code: " + dataEncryptionMethod.getCode());
    }
  }
}
