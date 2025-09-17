package no.elixir.crypt4gh.pojo.key;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import no.elixir.crypt4gh.pojo.key.kdf.Bcrypt;
import no.elixir.crypt4gh.pojo.key.kdf.SCrypt;

/** Key Derivation Function. */
public enum KDF {
  /** Scrypt key derivation function */
  SCRYPT,
  /**
   * Bcrypt_pbkdf key derivation function (mislabelled as Bcrypt, but Bcrypt is a password hashing
   * function and not a KDF)
   */
  BCRYPT,
  /** PBKDF2 key derivation function wrapping SHA256 */
  PBKDF2_HMAC_SHA256,
  /** No key derivation function */
  NONE;

  /** Key length for ChaCha20 (32 bytes = 256 bites) */
  public static final int KEY_LENGTH = 32;

  /** Name of the PBKDF2_hmac_SHA256 algorithm in the secret key factory */
  public static final String PBKDF_2_WITH_HMAC_SHA_256 = "PBKDF2WithHmacSHA256";

  /**
   * Derives a key from a password and salt using the key-derivation function (KDF).
   *
   * @param rounds Number of iterations.
   * @param password Password.
   * @param salt Salt.
   * @return Derived key.
   * @throws GeneralSecurityException If key can't be derived.
   */
  public byte[] derive(int rounds, char[] password, byte[] salt) throws GeneralSecurityException {
      return switch (this) {
          case SCRYPT -> SCrypt.scrypt(toBytes(password), salt, 1 << 14, 8, 1, KEY_LENGTH);
          case BCRYPT -> Bcrypt.bcrypt_pbkdf(toBytes(password), salt, rounds, KEY_LENGTH);
          case PBKDF2_HMAC_SHA256 -> {
              KeySpec spec = new PBEKeySpec(password, salt, rounds, KEY_LENGTH * 8);  // note: key length in bits
              SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF_2_WITH_HMAC_SHA_256);
              yield factory.generateSecret(spec).getEncoded();
          }
          case NONE -> throw new GeneralSecurityException("Can't derive key with 'none' KDF");
      };
  }

  /**
   * Converts a character array (encoded in UTF-8) into a byte array. The conversions does not
   * involve String creation for better security.
   *
   * @param chars a character array
   * @param a byte array
   */
  private byte[] toBytes(char[] chars) {
    CharBuffer charBuffer = CharBuffer.wrap(chars);
    ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
    byte[] bytes =
        Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
    Arrays.fill(byteBuffer.array(), (byte) 0);
    return bytes;
  }
}
