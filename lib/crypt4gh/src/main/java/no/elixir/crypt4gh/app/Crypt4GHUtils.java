package no.elixir.crypt4gh.app;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;
import no.elixir.crypt4gh.pojo.key.Format;
import no.elixir.crypt4gh.stream.Crypt4GHInputStream;
import no.elixir.crypt4gh.stream.Crypt4GHOutputStream;
import no.elixir.crypt4gh.util.KeyUtils;
import org.apache.commons.io.IOUtils;

/** Encryption/decryption utility class, not a public API. */
class Crypt4GHUtils {

  private static Crypt4GHUtils ourInstance = new Crypt4GHUtils();

  /**
   * Returns a singleton instance of this class.
   *
   * @return a Crypt4GHUtils object
   */
  static Crypt4GHUtils getInstance() {
    return ourInstance;
  }

  private KeyUtils keyUtils = KeyUtils.getInstance();
  private ConsoleUtils consoleUtils = ConsoleUtils.getInstance();

  /** The required minimum length for passwords protecting the private key file */
  private int minPwdLength = 8;

  private Crypt4GHUtils() {}

  /**
   * Generates a new X25519 key pair and saves the public and private keys to disk. The keys can be
   * saved in either OpenSSL or Crypt4GH format, and private keys saved in the Crypt4GH format can
   * be encrypted and protected with a chosen password. The public key file will have the suffix
   * ".pub.pem" and the private key file will have the suffix ".sec.pem".
   *
   * @param keyName the file name to use for the two key files
   * @param keyFormat should be either "CRYPT4GH" or "OPENSSL" (case-insensitive)
   * @param keyPassword a password used to encrypt the private key file
   * @throws Exception if the key files cannot be generated for various reasons
   * @see <a href="https://crypt4gh.readthedocs.io/en/latest/keys.html">Crypt4GH Key Format</a>
   */
  void generateX25519KeyPair(String keyName, String keyFormat, String keyPassword)
      throws Exception {
    KeyUtils keyUtils = KeyUtils.getInstance();
    KeyPair keyPair = keyUtils.generateKeyPair();
    File pubFile = new File(keyName + ".pub.pem");
    File secFile = new File(keyName + ".sec.pem");
    if (pubFile.exists() || secFile.exists()) {
      boolean replaceKeys =
          consoleUtils.promptForConfirmation(
              "Key files with that name already exist. Do you want to replace them?");
      if (!replaceKeys) return;
    }

    if (Format.CRYPT4GH.name().equalsIgnoreCase(keyFormat)) {
      char[] password;
      try {
        password =
            consoleUtils.readNewPassword(
                keyPassword, "Password for the private key: ", minPwdLength);
      } catch (IllegalArgumentException ex) {
        System.err.println("ERROR: " + ex.getMessage());
        return;
      }
      keyUtils.writeCrypt4GHKey(secFile, keyPair.getPrivate(), password);
      keyUtils.writeCrypt4GHKey(pubFile, keyPair.getPublic(), null);
    } else {
      keyUtils.writeOpenSSLKey(secFile, keyPair.getPrivate());
      keyUtils.writeOpenSSLKey(pubFile, keyPair.getPublic());
    }
    Set<PosixFilePermission> perms = new HashSet<>();
    perms.add(PosixFilePermission.OWNER_READ);
    perms.add(PosixFilePermission.OWNER_WRITE);
    Files.setPosixFilePermissions(secFile.toPath(), perms);
  }

  /**
   * Encrypts the specified data file in Crypt4GH format and saves the result to a new file. The
   * encrypted file will be named after the original with the suffix ".enc".
   *
   * @param dataFilePath the path to the file that should be encrypted
   * @param privateKeyFilePath the path to the sender's private key file
   * @param publicKeyFilePath the path to the recipient's public key file
   * @throws GeneralSecurityException In case the Crypt4GH header is malformed
   * @throws IOException if an I/O error occurs
   * @throws IllegalArgumentException if the public or private key file cannot be found or if the
   *     private key cannot be decrypted
   */
  void encryptFile(String dataFilePath, String privateKeyFilePath, String publicKeyFilePath)
      throws IOException, GeneralSecurityException {
    File dataInFile = new File(dataFilePath);
    File dataOutFile = new File(dataFilePath + ".enc");
    if (dataOutFile.exists()
        && !ConsoleUtils.getInstance()
            .promptForConfirmation(dataOutFile.getAbsolutePath() + " already exists. Overwrite?")) {
      return;
    }
    PrivateKey privateKey = null;
    try {
      privateKey = readPrivateKey(privateKeyFilePath);
    } catch (java.nio.file.NoSuchFileException missingFileEx) {
      throw new IllegalArgumentException(
          "ERROR: Private key file not found: " + privateKeyFilePath);
    } catch (javax.crypto.AEADBadTagException badTagEx) {
      throw new IllegalArgumentException(
          "ERROR: Unable to decrypt private key file. The password is probably wrong!");
    }
    PublicKey publicKey = null;
    try {
      publicKey = keyUtils.readPublicKey(new File(publicKeyFilePath));
    } catch (java.nio.file.NoSuchFileException missingFileEx) {
      throw new IllegalArgumentException("ERROR: Public key file not found: " + publicKeyFilePath);
    }
    try (InputStream inputStream = new FileInputStream(dataInFile);
        OutputStream outputStream = new FileOutputStream(dataOutFile);
        Crypt4GHOutputStream crypt4GHOutputStream =
            new Crypt4GHOutputStream(outputStream, privateKey, publicKey)) {
      System.out.println("Encryption initialized...");
      IOUtils.copyLarge(inputStream, crypt4GHOutputStream);
      System.out.println("Done: " + dataOutFile.getAbsolutePath());
    } catch (FileNotFoundException fileNotFoundEx) {
      throw new IllegalArgumentException("ERROR: Input file not found: " + dataFilePath);
    } catch (GeneralSecurityException e) {
      System.err.println(e.getMessage());
      dataOutFile.delete();
    }
  }

  /**
   * Decrypts the specified file in Crypt4GH format and saves the result to a new file. The
   * decrypted file will be named after the original with the suffix ".dec".
   *
   * @param dataFilePath the path to the file that should be decrypted
   * @param privateKeyFilePath the path to the recipient's private key file
   * @throws GeneralSecurityException In case the Crypt4GH header is malformed
   * @throws IOException if an I/O error occurs
   * @throws IllegalArgumentException if the private key file cannot be found or cannot be decrypted
   */
  void decryptFile(String dataFilePath, String privateKeyFilePath)
      throws IOException, GeneralSecurityException {
    File dataInFile = new File(dataFilePath);
    File dataOutFile = new File(dataFilePath + ".dec");
    if (dataOutFile.exists()
        && !ConsoleUtils.getInstance()
            .promptForConfirmation(dataOutFile.getAbsolutePath() + " already exists. Overwrite?")) {
      return;
    }
    PrivateKey privateKey = null;
    try {
      privateKey = readPrivateKey(privateKeyFilePath);
    } catch (java.nio.file.NoSuchFileException missingFileEx) {
      throw new IllegalArgumentException(
          "ERROR: Private key file not found: " + privateKeyFilePath);
    } catch (javax.crypto.AEADBadTagException badTagEx) {
      throw new IllegalArgumentException(
          "ERROR: Unable to decrypt private key file. The password is probably wrong!");
    }
    System.out.println("Decryption initialized...");
    try (FileInputStream inputStream = new FileInputStream(dataInFile);
        OutputStream outputStream = new FileOutputStream(dataOutFile);
        Crypt4GHInputStream crypt4GHInputStream =
            new Crypt4GHInputStream(inputStream, privateKey)) {
      IOUtils.copyLarge(crypt4GHInputStream, outputStream);
      System.out.println("Done: " + dataOutFile.getAbsolutePath());
    } catch (FileNotFoundException fileNotFoundEx) {
      throw new IllegalArgumentException("ERROR: Input file not found: " + dataFilePath);
    } catch (GeneralSecurityException e) {
      System.err.println(e.getMessage());
      dataOutFile.delete();
    }
  }

  /**
   * Reads and returns a private key from a file (in OpenSSL or Crypt4GH format).
   *
   * @param privateKeyFilePath path to the private key file
   * @return private key
   * @throws IOException If the file can't be read
   * @throws GeneralSecurityException If the key can't be constructed from the given file
   */
  private PrivateKey readPrivateKey(String privateKeyFilePath)
      throws IOException, GeneralSecurityException {
    PrivateKey privateKey;
    try {
      privateKey = keyUtils.readPrivateKey(new File(privateKeyFilePath), null);
    } catch (IllegalArgumentException e) {
      char[] password = System.console().readPassword("Password for the private key: ");
      privateKey = keyUtils.readPrivateKey(new File(privateKeyFilePath), password);
    }
    return privateKey;
  }
}
