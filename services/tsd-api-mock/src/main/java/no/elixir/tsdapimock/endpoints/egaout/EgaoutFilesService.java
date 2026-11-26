package no.elixir.tsdapimock.endpoints.egaout;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.core.resumables.Resumables;
import no.elixir.tsdapimock.core.utils.JwtService;
import no.elixir.tsdapimock.endpoints.egaout.dto.OutboxFileListingDto;
import no.elixir.tsdapimock.endpoints.egaout.dto.TSDFileDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EgaoutFilesService {

  private static final int DEFAULT_PER_PAGE = 100;
  private static final int MAX_PER_PAGE = 50000;

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

  /**
   * Lists one page of the user's outbox, following the TSD file API: {@code page} is zero-based,
   * {@code per_page} is read only when {@code page} is given, and the returned {@code page} is the
   * link to the next page, or null on the last one.
   */
  public OutboxFileListingDto listOutboxFiles(
      String authorizationHeader,
      String project,
      String userName,
      String page,
      String perPage,
      String baseUri) {
    if (!authorizationHeader.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Header must contain a bearer auth token");
    }
    if (!jwtService.verify(authorizationHeader)) {
      throw new CredentialsMismatchException("Stream processing failed");
    }

    long currentPage = 0;
    long pageSize = DEFAULT_PER_PAGE;
    if (page != null) {
      try {
        currentPage = Long.parseLong(page);
        if (perPage != null) {
          pageSize = Long.parseLong(perPage);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("next values must be integers");
      }
    }
    if (currentPage < 0) {
      throw new IllegalArgumentException("next values are natural numbers");
    }
    if (pageSize > MAX_PER_PAGE) {
      throw new IllegalArgumentException("per_page cannot exceed " + MAX_PER_PAGE);
    }

    boolean createUserDirectoryIfNotExists = false;
    // The directory order is unspecified, and a page must hold the same files on every request.
    List<File> files =
        resumables.listFiles(project, userName, createUserDirectoryIfNotExists).stream()
            .filter(File::isFile)
            .sorted(Comparator.comparing(File::getName))
            .toList();

    // Past the end, or a per_page below one, is an empty page; the clamp also keeps a huge page
    // number from overflowing the offset.
    long offset =
        pageSize <= 0 || currentPage > files.size() ? files.size() : currentPage * pageSize;
    List<TSDFileDto> tsdFiles =
        files.stream()
            .skip(offset)
            .limit(Math.max(pageSize, 0))
            .map(file -> createTSDFileDtoFromFile(file, userName))
            .toList();

    boolean hasMore = offset + tsdFiles.size() < files.size();
    String nextPage = hasMore ? baseUri + "?page=" + (currentPage + 1) : null;
    return new OutboxFileListingDto(tsdFiles, nextPage);
  }
}
