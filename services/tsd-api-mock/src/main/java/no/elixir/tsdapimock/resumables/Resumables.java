package no.elixir.tsdapimock.resumables;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Slf4j
@Service
public class Resumables {

  private final ResumablesRepository resumablesRepository;

  @Value("${tsd.elixir.import}")
  public String tsdElixirImport;

  @Autowired
  public Resumables(ResumablesRepository resumablesRepository) {
    this.resumablesRepository = resumablesRepository;
  }

  /**
   * Converts a ResumableUpload entity to a ResumableUploadDto object.
   *
   * @param entity The ResumableUpload entity to convert.
   * @return A ResumableUploadDto object representing the converted entity.
   */
  public static ResumableUploadDto convertToDto(ResumableUpload entity) {
    return new ResumableUploadDto(
        entity.getId(),
        entity.getFileName(),
        entity.getMemberGroup(),
        entity.getChunkSize(),
        entity.getPreviousOffset(),
        entity.getNextOffset(),
        entity.getMaxChunk(),
        entity.getMd5Sum());
  }

  public ResumableUpload getResumableUpload(String id) {
    return resumablesRepository.findById(id).orElseThrow();
  }

  /**
   * Reads all the resumable chunks from the repository.
   *
   * @return An ArrayList of ResumableUpload objects representing the resumable chunks.
   */
  public ArrayList<ResumableUpload> readResumableChunks() {
    return new ArrayList<>((Collection<? extends ResumableUpload>) resumablesRepository.findAll());
  }

  public ResumableUpload processChunk(
      String userName,
      String project,
      String filename,
      String chunk,
      byte[] content,
      ResumableUpload resumableUpload)
      throws IOException {
    File uploadFolder =
        generateUploadFolder(tsdElixirImport.formatted(project, userName), resumableUpload.getId());

    if ("end".equalsIgnoreCase(chunk)) {
      finalizeChunks(userName, uploadFolder, resumableUpload.getId(), project);
      return resumableUpload;
    }
    if ("1".equals(chunk)) {
      File chunkFile = saveChunk(uploadFolder, filename, content);
      return createResumableUpload(chunkFile, resumableUpload);
    }

    File chunkFile = saveChunk(uploadFolder, chunk, filename, content);
    return updateResumableUpload(resumableUpload, chunkFile);
  }

  private ResumableUpload createResumableUpload(File chunkFile, ResumableUpload resumableUpload)
      throws IOException {
    long length = chunkFile.length();
    resumableUpload.setPreviousOffset(BigInteger.ZERO);
    resumableUpload.setNextOffset(BigInteger.valueOf(length));
    resumableUpload.setChunkSize(BigInteger.valueOf(length));
    resumableUpload.setMd5Sum(DigestUtils.md5DigestAsHex(Files.newInputStream(chunkFile.toPath())));
    return resumablesRepository.save(resumableUpload);
  }

  private ResumableUpload updateResumableUpload(ResumableUpload resumableUpload, File chunkFile)
      throws IOException {
    long length = chunkFile.length();
    resumableUpload.setPreviousOffset(resumableUpload.getNextOffset());
    resumableUpload.setNextOffset(resumableUpload.getNextOffset().add(BigInteger.valueOf(length)));
    resumableUpload.setChunkSize(BigInteger.valueOf(length));
    resumableUpload.setMd5Sum(DigestUtils.md5DigestAsHex(Files.newInputStream(chunkFile.toPath())));
    return resumablesRepository.save(resumableUpload);
  }

  public File generateUploadFolder(String basePath, String uploadId) {
    File uploadDir = new File(basePath, uploadId);
    if (!uploadDir.exists()) {
      boolean isCreated = uploadDir.mkdirs();
      if (isCreated) {
        log.info("Upload directory created: " + uploadDir.getAbsolutePath());
      } else {
        log.warn("Failed to create upload directory: " + uploadDir.getAbsolutePath());
      }
    }
    return uploadDir;
  }

  private File saveChunk(File uploadFolder, String fileName, byte[] content) throws IOException {
    return saveChunk(uploadFolder, "1", fileName, content);
  }

  private File saveChunk(File uploadFolder, String chunk, String fileName, byte[] content)
      throws IOException {
    File chunkFile = createChunkFile(uploadFolder, fileName, Integer.parseInt(chunk));
    log.info("Saving chunk " + chunk + " to " + chunkFile.getAbsolutePath());
    Files.write(chunkFile.toPath(), content);
    return chunkFile;
  }

  private File createChunkFile(File dir, String fileName, int chunkNumber) {
    String chunkFileName = fileName.concat(".chunk." + chunkNumber);
    return new File(dir, chunkFileName);
  }

  private void finalizeChunks(String userName, File uploadFolder, String id, String project)
      throws IOException {
    ResumableUpload resumable =
        resumablesRepository
            .findById(id)
            .orElseThrow(
                () -> new FileNotFoundException("Resumable upload not found with ID: " + id));

    mergeFiles(userName, uploadFolder, resumable, project);
    resumablesRepository.deleteById(id);
  }

  private void mergeFiles(String userName, File dir, ResumableUpload resumable, String project)
      throws IOException {
    File destinationFile =
        new File(tsdElixirImport.formatted(project, userName), resumable.getFileName());

    try (OutputStream outputStream =
        Files.newOutputStream(
            destinationFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
      for (int i = 1; i <= resumable.getMaxChunk().intValue(); i++) {
        File chunkFile = createChunkFile(dir, resumable.getFileName(), i);
        log.info("Merging chunk: " + chunkFile.getAbsolutePath());

        try (InputStream inputStream = Files.newInputStream(chunkFile.toPath())) {
          IOUtils.copy(inputStream, outputStream);
        }
        Files.delete(chunkFile.toPath());
      }
    } catch (Exception e) {
      log.error("Error merging files: " + e.getMessage());
      throw e;
    }

    log.info("Deleting upload directory: " + dir.getAbsolutePath());
    Files.deleteIfExists(dir.toPath());
  }

  public void deleteFiles(File dir, ResumableUpload resumable) throws IOException {
    String fileName = resumable.getFileName();
    for (int i = 1; i <= resumable.getMaxChunk().intValue(); i++) {
      File chunkFile = createChunkFile(dir, fileName, i);
      log.info("DELETING" + chunkFile.toPath());
      Files.delete(chunkFile.toPath());
    }
    log.info("DELETING" + dir.toPath());
    Files.delete(dir.toPath());
  }
}
