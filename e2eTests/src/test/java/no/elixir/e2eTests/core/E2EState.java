package no.elixir.e2eTests.core;

import java.io.File;
import java.security.KeyPair;
import no.elixir.crypt4gh.util.KeyUtils;
import no.elixir.e2eTests.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared E2E test state across multiple test classes.
 *
 * <p>In JUnit, test state is not preserved between different test classes. This class uses static
 * variables as a workaround so that data and utilities can be accessed globally by all E2E test
 * classes.
 *
 * <p>Example usage: E2EState.log.info("message");
 */
public class E2EState {

  public static final Environment env = new Environment();
  public static final Logger log = LoggerFactory.getLogger(E2EState.class);
  public static final KeyUtils keyUtils = KeyUtils.getInstance();

  public static File rawFile;
  public static File encFile;
  public static String rawSHA256Checksum;
  public static String encSHA256Checksum;
  public static String rawMD5Checksum;
  public static String stableId;
  public static String datasetId;
  public static String archivePath;
  public static String correlationId;
  public static KeyPair senderKeypair;
  public static KeyPair recipientKeypair;
}
