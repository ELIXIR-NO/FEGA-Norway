package no.elixir.tsdapimock.ega;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.stream.Collectors;
import no.elixir.tsdapimock.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.exceptions.FileProcessingException;
import no.elixir.tsdapimock.resumables.*;
import no.elixir.tsdapimock.utils.JwtService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
