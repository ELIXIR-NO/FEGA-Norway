package no.elixir.tsdapimock.endpoints.ega;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.core.exceptions.FileProcessingException;
import no.elixir.tsdapimock.core.resumables.*;
import no.elixir.tsdapimock.core.utils.JwtService;
import no.elixir.tsdapimock.endpoints.egaout.dto.OutboxFileListingDto;
import no.elixir.tsdapimock.endpoints.egaout.dto.TSDFileDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EgaFilesService {

  private final JwtService jwtService;
  private final Resumables resumables;

  private final ResumablesRepository resumablesRepository;

  @Autowired
  public EgaFilesService(
      JwtService jwtService, Resumables resumables, ResumablesRepository resumablesRepository) {
    this.jwtService = jwtService;
    this.resumables = resumables;
    this.resumablesRepository = resumablesRepository;
  }

  public ResumableUploadsResponseDto getResumableUploads(
      String project, String userName, String authorization) {
    if (!authorization.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Header must contains a Bearer token");
    }
    if (!jwtService.verify(authorization)) {
      throw new CredentialsMismatchException("Invalid authorization token");
    }
    var resumableChunks = resumables.readResumableChunks();
    ArrayList<ResumableUploadDto> dtoList =
        resumableChunks.stream()
            .map(Resumables::convertToDto)
            .collect(Collectors.toCollection(ArrayList::new));

    return new ResumableUploadsResponseDto(dtoList);
  }

  public ResumableUploadDto handleResumableUpload(
      String project,
      String filename,
      String authorizationHeader,
      String userName,
      String chunk,
      String id,
      byte[] content)
      throws IllegalArgumentException, CredentialsMismatchException {

    if (!jwtService.verify(authorizationHeader)) {
      throw new CredentialsMismatchException("Invalid Authorization");
    }

    if (StringUtils.isEmpty(filename)) {
      throw new IllegalArgumentException("Filename cannot be empty");
    }

    ResumableUpload resumableUpload;
    if (StringUtils.isEmpty(id)) {
      resumableUpload = new ResumableUpload();
      resumableUpload.setFileName(filename);
      resumableUpload.setMaxChunk(new BigInteger(chunk));
      resumablesRepository.save(resumableUpload);
    } else {
      resumableUpload =
          resumablesRepository
              .findById(id)
              .orElseThrow(() -> new IllegalArgumentException("Invalid upload ID"));
    }
    ResumableUpload uploadedResumable;
    try {
      uploadedResumable =
          resumables.processChunk(userName, project, filename, chunk, content, resumableUpload);
    } catch (IOException e) {
      throw new FileProcessingException(e.getMessage());
    }

    return Resumables.convertToDto(uploadedResumable);
  }

  public OutboxFileListingDto listInboxFiles(
      String authorizationHeader, String project, String userName) {
    if (!authorizationHeader.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Header must contain a bearer auth token");
    }
    if (!jwtService.verify(authorizationHeader)) {
      throw new CredentialsMismatchException("Stream processing failed");
    }
    boolean createUserDirectoryIfNotExists = false;
    List<File> files = resumables.listInboxFiles(project, userName, createUserDirectoryIfNotExists);

    List<TSDFileDto> tsdFiles = new ArrayList<>();
    for (File file : files) {
      if (file.isFile()) {
        tsdFiles.add(createTSDFileDtoFromFile(file, userName));
      }
    }

    final String PAGE_NUMBER = "1";
    return new OutboxFileListingDto(tsdFiles, PAGE_NUMBER);
  }

  /** Creates a TSDFileDto record from a File object. */
  private static TSDFileDto createTSDFileDtoFromFile(File file, String owner) {
    try {
      Path path = file.toPath();
      BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
      String modifiedDate = formatter.format(attrs.lastModifiedTime().toInstant());

      String mimeType = Files.probeContentType(path);
      if (mimeType == null) {
        mimeType = "application/octet-stream";
      }

      return new TSDFileDto(
          file.getName(),
          file.length(),
          modifiedDate,
          "/files/" + file.getName(),
          true,
          null,
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
}
