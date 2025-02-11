package no.elixir.crypt4gh.pojo.key;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Testing the supported Key Derivation Functions */
public class KDFTest {

  private char[] password = "password".toCharArray();
  private byte[] salt = new byte[] {1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4};
  private int rounds = 4;

  @Test
  public void scryptTest() throws Exception {
    byte[] hash = KDF.valueOf("SCRYPT").derive(rounds, password, salt);
    assertArrayEquals(
        hexToBytes("1ac7b37b2173dcc95dd158c880e6de2caed7fcb0530ba86d343497b6cf6cd71f"),
        hash,
        "Incorrect Scrypt hash");
  }

  @Test
  public void bcryptTest() throws Exception {
    byte[] hash = KDF.valueOf("BCRYPT").derive(rounds, password, salt);
    assertArrayEquals(
        hexToBytes("f89795089a19a4f990a30ea1563cac4fa7e4655aea290219e88902a3125c351b"),
        hash,
        "Incorrect Bcrypt hash");
  }

  @Test
  public void pbkdf2Test() throws Exception {
    byte[] hash = KDF.valueOf("PBKDF2_HMAC_SHA256").derive(rounds, password, salt);
    assertArrayEquals(
        hexToBytes("dd3352defb9aa734875f7a32b60e4bcf9e3671216d6e0c39f135f0297bf8e121"),
        hash,
        "Incorrect PBKDF2 hash");
  }

  private byte[] hexToBytes(String hex) {
    int length = hex.length();
    if (length % 2 != 0) {
      throw new IllegalArgumentException("Hex string must have an even length");
    }
    byte[] byteArray = new byte[length / 2];
    for (int i = 0; i < length; i += 2) {
      byteArray[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
    }
    return byteArray;
  }
}
