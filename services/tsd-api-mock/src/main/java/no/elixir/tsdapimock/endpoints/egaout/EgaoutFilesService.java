package no.elixir.tsdapimock.endpoints.egaout;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import no.elixir.tsdapimock.endpoints.egaout.dto.OutboxFileListingDto;
import no.elixir.tsdapimock.endpoints.egaout.dto.TSDFileDto;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.core.resumables.Resumables;
import no.elixir.tsdapimock.core.utils.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EgaoutFilesService {

  private final JwtService jwtService;
  private final Resumables resumables;

  @Autowired
  public EgaoutFilesService(JwtService jwtService, Resumables resumables) {
    this.jwtService = jwtService;
    this.resumables = resumables;
  }

  /** Creates a TSDFileDto record from a File object */
  private static TSDFileDto createTSDFileDtoFromFile(File file, String owner) {
    try {
      Path path = file.toPath();
      BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

      // Format the modified date
      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
      String modifiedDate = formatter.format(attrs.lastModifiedTime().toInstant());

      // Determine MIME type
      String mimeType = Files.probeContentType(path);
      if (mimeType == null) {
        mimeType = "application/octet-stream";
      }

      return new TSDFileDto(
          file.getName(),
          file.length(),
          modifiedDate,
          "/files/" + file.getName(), // Mock href
          true, // Default exportable to true
          null, // No reason for exportable files
          mimeType,
          owner);
    } catch (Exception e) {
      log.error("Error reading file attributes for: {}", file.getName(), e);
      return new TSDFileDto(
          file.getName(),
          file.length(),
          "Unknown",
          "/files/" + file.getName(),
          false,
          "Error reading file attributes",
          "application/octet-stream",
          owner);
    }
  }

  public OutboxFileListingDto listOutboxFiles(
      String authorizationHeader, String project, String userName) {
    if (!authorizationHeader.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Header must contain a bearer auth token");
    }
    if (!jwtService.verify(authorizationHeader)) {
      throw new CredentialsMismatchException("Stream processing failed");
    }
    boolean createUserDirectoryIfNotExists = false;
    List<File> files = resumables.listFiles(project, userName, createUserDirectoryIfNotExists);

    // Convert to list of TSDFile objects
    List<TSDFileDto> tsdFiles = new ArrayList<>();
    for (File file : files) {
      if (file.isFile()) {
        TSDFileDto tsdFile = createTSDFileDtoFromFile(file, userName);
        tsdFiles.add(tsdFile);
      }
    }

    // Note: Regardless of the page number, resumables.listFiles
    // will return all files in the user's outbox.
    // See: https://github.com/ELIXIR-NO/FEGA-Norway/issues/561
    final String PAGE_NUMBER = "1";
    return new OutboxFileListingDto(tsdFiles, PAGE_NUMBER);
  }
}
