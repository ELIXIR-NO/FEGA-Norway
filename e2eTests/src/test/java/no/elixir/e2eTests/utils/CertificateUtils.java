package no.elixir.e2eTests.utils;

import no.elixir.e2eTests.core.State;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

public class CertificateUtils {

  /**
   * Retrieves a file from a specified path in a running Docker container. The file is copied to a
   * temporary file in memory and returned as a File instance. Assuming that this test invoked as a
   * process in the host machine using the build system.
   *
   * @param containerNameOrId The name or ID of the running Docker container.
   * @param containerPath The path inside the container to the certificate file.
   * @return File instance of the certificate, stored in a temporary file.
   * @throws Exception If file retrieval or creation fails.
   */
  public static File getFileInContainer(String containerNameOrId, String containerPath)
      throws Exception {

    // Extract the file name and extension from the container path
    Path pathInContainer = Paths.get(containerPath);
    String fileName =
        pathInContainer
            .getFileName()
            .toString(); // Extracts "ega.pub.pem" from "/storage/certs/ega.pub.pem"

    // Create a temporary file with the extracted file name and extension
    Path tempFilePath = Files.createTempFile(fileName, null);
    File tempFile = tempFilePath.toFile();
    tempFile.deleteOnExit(); // Ensure it gets deleted when JVM exits

    // Define the Docker copy command
    String command =
        String.format(
            "docker cp %s:%s %s", containerNameOrId, containerPath, tempFile.getAbsolutePath());

    // Execute the Docker command
    ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
    Process process = processBuilder.start();
    int exitCode = process.waitFor();

    // Check if the command executed successfully
    if (exitCode != 0) {
      throw new IOException("Failed to copy file from container. Exit code: " + exitCode);
    }

    return tempFile; // Return the temporary file containing the certificate
  }

  /**
   * Retrieves a certificate file from a local folder inside the container. Assumes that the folder
   * is mapped and directly accessible.
   *
   * @param fileName The name of the certificate file to retrieve.
   * @return File instance pointing to the certificate file.
   * @throws FileNotFoundException If the file does not exist in the specified folder.
   */
  public static File getFileFromLocalFolder(String dir, String fileName)
      throws FileNotFoundException {
    Path filePath = Paths.get(dir, fileName);
    File file = filePath.toFile();

    if (!file.exists()) {
      throw new FileNotFoundException("Certificate file not found: " + file.getAbsolutePath());
    }

    return file;
  }

  /**
   * Retrieves a file from either a local Docker container or directly from the mapped volume,
   * depending on the test runtime environment.
   *
   * @param name The name of the certificate file.
   * @return File instance of the certificate.
   * @throws Exception If file retrieval fails.
   */
  public static File getCertificateFile(String name) throws Exception {
    if ("local".equalsIgnoreCase(State.env.getRuntime())) {
      // Use getFileInContainer for local development
      return CertificateUtils.getFileInContainer("file-orchestrator", "/storage/certs/" + name);
    } else {
      // Assuming this test code is run inside a docker container.
      return CertificateUtils.getFileFromLocalFolder("/storage/certs/", name);
    }
  }

  /**
   * Creates ssl contexts for services such as: RabbitMQ
   *
   * @return SSLContext
   * @throws Exception Unable to load the store.
   */
  public static SSLContext createSslContext() throws Exception {
    // Load the PKCS12 trust store
    File rootCA = getCertificateFile("truststore.p12");
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(new FileInputStream(rootCA), State.env.getTruststorePassword().toCharArray());
    // Create trust manager
    TrustManagerFactory trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);
    // Create and initialize the SSLContext
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

    return sslContext;
  }

}
