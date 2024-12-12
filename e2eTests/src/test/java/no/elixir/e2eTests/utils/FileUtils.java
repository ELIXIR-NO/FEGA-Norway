package no.elixir.e2eTests.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

public class FileUtils {

  /**
   * Generates a file with random content.
   *
   * @param basePath the base directory for the generated file
   * @param fileSize the size of the file in bytes
   * @return the generated file
   * @throws IOException if an I/O error occurs
   */
  public static File createRandomFile(String basePath, long fileSize) throws IOException {
    File file = new File(basePath + UUID.randomUUID() + ".raw");

    try (FileOutputStream fos = new FileOutputStream(file)) {
      byte[] buffer = new byte[1024]; // 1 KB buffer
      Random random = new Random();
      long bytesWritten = 0;

      while (bytesWritten < fileSize) {
        random.nextBytes(buffer); // Fill buffer with random bytes
        int bytesToWrite = (int) Math.min(buffer.length, fileSize - bytesWritten);
        fos.write(buffer, 0, bytesToWrite);
        bytesWritten += bytesToWrite;
      }
    }

    return file;
  }
}
