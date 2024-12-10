package no.elixir.e2eTests.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CertificateUtil {

  /**
   * Retrieves a file from a specified path in a running Docker container. The file is copied to a
   * temporary file in memory and returned as a File instance.
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
    ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
    Process process = processBuilder.start();
    int exitCode = process.waitFor();

    // Check if the command executed successfully
    if (exitCode != 0) {
      throw new IOException("Failed to copy file from container. Exit code: " + exitCode);
    }

    return tempFile; // Return the temporary file containing the certificate
  }
}
