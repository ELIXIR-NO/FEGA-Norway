package no.elixir.tsdapimock.files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import no.elixir.tsdapimock.ega.dto.FileUploadMessageDto;
import no.elixir.tsdapimock.ega.dto.FolderMessageDto;
import no.elixir.tsdapimock.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.exceptions.FailedResourceCreationException;
import no.elixir.tsdapimock.exceptions.FailedResourceDeletionException;
import no.elixir.tsdapimock.exceptions.FileProcessingException;
import no.elixir.tsdapimock.files.dto.DeleteResumableDto;
import no.elixir.tsdapimock.resumables.*;
import no.elixir.tsdapimock.utils.JwtService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FilesService {
  private final JwtService jwtService;
  private final ResumablesRepository resumablesRepository;
  private final Resumables resumables;

  @Value("${tsd.file.import}")
  public String tsdFileImport;

  @Autowired
  public FilesService(
      Resumables resumables, ResumablesRepository resumablesRepository, JwtService jwtService) {
    this.resumables = resumables;
    this.resumablesRepository = resumablesRepository;
    this.jwtService = jwtService;
  }

  public FileUploadMessageDto upload(
      String project, String authorizationHeader, String fileName, InputStream fileStream) {
    if (!authorizationHeader.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Header must contain a bearer auth token");
    }
    if (!jwtService.verify(authorizationHeader)) {
      throw new CredentialsMismatchException("Stream processing failed");
    }

    var path = Paths.get(String.format(tsdFileImport, project), fileName);
    try {
      Files.copy(fileStream, path, StandardCopyOption.REPLACE_EXISTING);
      log.info(path.getParent().toString());
    } catch (IOException e) {
      log.error(e.getMessage());
    }
    return new FileUploadMessageDto("Data Streamed");
  }

  public FolderMessageDto createFolder(
      String project, String authorizationHeader, String folderName) {
    if (!jwtService.verify(authorizationHeader)) {
      throw new CredentialsMismatchException("Invalid bearer authorization token");
    }
    var path = Paths.get(String.format(tsdFileImport, project), folderName);
    try {
      Files.createDirectories(path);
      log.info("created: " + path);
    } catch (IOException e) {
      throw new FailedResourceCreationException(e.getClass().getTypeName() + e.getMessage());
    }
    return new FolderMessageDto("folder created");
  }

  public ResumableUploadsResponseDto getResumableUploads(
      String project, String authorizationHeader) {
    if (!jwtService.verify(authorizationHeader)) {
      throw new CredentialsMismatchException("Invalid Authorization Header");
    }
    var resumableChunks = resumables.readResumableChunks();
    ArrayList<ResumableUploadDto> dtoList =
        resumableChunks.stream()
            .map(Resumables::convertToDto)
            .collect(Collectors.toCollection(ArrayList::new));

    return new ResumableUploadsResponseDto(dtoList);
  }

  // TODO: validate this
  public ResumableUploadDto handleResumableUpload(
      String project,
      String filename,
      String authorizationHeader,
      String fileName,
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
          resumables.processChunk(null, project, filename, chunk, content, resumableUpload);
    } catch (IOException e) {
      throw new FileProcessingException(e.getMessage());
    }

    return Resumables.convertToDto(uploadedResumable);
  }

  public DeleteResumableDto deleteResumableUpload(
      String project, String authorization, String fileName, String id) {
    if (!authorization.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Authorization must be a bearer token");
    }
    if (!jwtService.verify(authorization)) {
      throw new CredentialsMismatchException("Invalid Authorization");
    }
    if (StringUtils.isEmpty(fileName)) {
      return new DeleteResumableDto("Stream processing failed");
    }
    File uploadFolder =
        resumables.generateUploadFolder(String.format(tsdFileImport, project), id);
    try {
      ResumableUpload resumableUpload = resumables.getResumableUpload(id);
      resumables.deleteFiles(uploadFolder, resumableUpload);
      resumablesRepository.delete(resumableUpload);
    } catch (Exception e) {
      throw new FailedResourceDeletionException("ERROR deleting resource");
    }

    return new DeleteResumableDto("Resumable deleted");
  }
}
