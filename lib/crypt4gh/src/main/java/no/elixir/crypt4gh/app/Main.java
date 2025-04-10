package no.elixir.crypt4gh.app;

import org.apache.commons.cli.*;

/** Console application for encrypting/decrypting files. */
public class Main {

  /** Command-line option for generating a new key pair */
  public static final String GENERATE = "g";

  /** Command-line option for encrypting a file */
  public static final String ENCRYPT = "e";

  /** Command-line option for decrypting a file */
  public static final String DECRYPT = "d";

  /** Command-line option for specifying the key format (OpenSSL or Crypt4GH) */
  public static final String KEY_FORMAT = "kf";

  /** Command-line option for specifying the password for the private key file */
  public static final String KEY_PASSWORD = "kp";

  /** Command-line option for the public key file */
  public static final String PUBLIC_KEY = "pk";

  /** Command-line option for the private key file */
  public static final String SECRET_KEY = "sk";

  /** Command-line option to display the version number */
  public static final String VERSION = "v";

  /** Command-line option to display the help page */
  public static final String HELP = "h";

  /**
   * Main method, entry-point to the application.
   *
   * @param args Command line arguments.
   * @throws Exception If there's some error.
   */
  public static void main(String[] args) throws Exception {
    Options options = new Options();

    OptionGroup mainOptions = new OptionGroup();
    Option generateKeyOption =
        new Option(GENERATE, "generate", true, "generate key pair (specify desired key name)");
    mainOptions.addOption(generateKeyOption);
    mainOptions.addOption(
        new Option(ENCRYPT, "encrypt", true, "encrypt the file (specify file to encrypt)"));
    mainOptions.addOption(
        new Option(DECRYPT, "decrypt", true, "decrypt the file (specify file to decrypt)"));
    mainOptions.addOption(new Option(VERSION, "version", false, "print application's version"));
    mainOptions.addOption(new Option(HELP, "help", false, "print this message"));
    options.addOptionGroup(mainOptions);

    options.addOption(
        new Option(
            KEY_FORMAT,
            "keyform",
            true,
            "key format to use for generated keys (OpenSSL or Crypt4GH)"));
    options.addOption(
        new Option(
            KEY_PASSWORD,
            "keypass",
            true,
            "password for Crypt4GH private key (will be prompted afterwards if skipped)"));
    options.addOption(
        new Option(PUBLIC_KEY, "pubkey", true, "public key to use (specify key file)"));
    options.addOption(
        new Option(SECRET_KEY, "seckey", true, "secret key to use (specify key file)"));

    CommandLineParser parser = new DefaultParser();
    Crypt4GHUtils crypt4GHUtils = Crypt4GHUtils.getInstance();
    try {
      CommandLine line = parser.parse(options, args);
      if (line.getOptions().length == 0) {
        printHelp(options);
        return;
      }
      if (line.hasOption(HELP)) {
        printHelp(options);
      } else if (line.hasOption(VERSION)) {
        printVersion();
      } else if (line.hasOption(GENERATE)) {
        crypt4GHUtils.generateX25519KeyPair(
            line.getOptionValue(GENERATE),
            line.getOptionValue(KEY_FORMAT),
            line.getOptionValue(KEY_PASSWORD));
      } else {
        if (line.hasOption(ENCRYPT)) {
          if (!line.hasOption(PUBLIC_KEY)) {
            System.err.println("Missing argument for option: " + PUBLIC_KEY);
            return;
          }
          if (!line.hasOption(SECRET_KEY)) {
            System.err.println("Missing argument for option: " + SECRET_KEY);
            return;
          }
          crypt4GHUtils.encryptFile(
              line.getOptionValue(ENCRYPT),
              line.getOptionValue(SECRET_KEY),
              line.getOptionValue(PUBLIC_KEY));
        } else if (line.hasOption(DECRYPT)) {
          if (!line.hasOption(SECRET_KEY)) {
            System.err.println("Missing argument for option: " + SECRET_KEY);
            return;
          }
          crypt4GHUtils.decryptFile(line.getOptionValue(DECRYPT), line.getOptionValue(SECRET_KEY));
        }
      }
    } catch (ParseException exp) {
      System.err.println(exp.getMessage());
    } catch (IllegalArgumentException ex) {
      System.err.println(ex.getMessage());
    }
  }

  /**
   * Prints out the version number for this release of Crypt4GH. The version is read from the
   * Manifest file.
   */
  private static void printVersion() {
    String implementationVersion = Main.class.getPackage().getImplementationVersion();
    System.out.println("Crypt4GH " + implementationVersion);
  }

  /**
   * Prints out a help page with descriptions of all command-line arguments.
   *
   * @param options Command line arguments.
   */
  private static void printHelp(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(
        "crypt4gh",
        "\nCrypt4GH encryption/decryption tool\n\n",
        options,
        "\nRead more about the format at http://samtools.github.io/hts-specs/crypt4gh.pdf\n",
        true);
  }
}
