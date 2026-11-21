package no.elixir.fega.ltp.controllers.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import no.elixir.fega.ltp.aspects.AuthAspect;
import no.elixir.fega.ltp.aspects.NoEgaAuth;
import no.elixir.fega.ltp.aspects.ProcessArgumentsAspect;
import no.elixir.fega.ltp.errors.ErrorCode;
import no.elixir.fega.ltp.errors.ProxyException;
import no.uio.ifi.tc.TSDFileAPIClient;
import no.uio.ifi.tc.model.pojo.Chunk;
import no.uio.ifi.tc.model.pojo.Message;
import no.uio.ifi.tc.model.pojo.ResumableUpload;
import no.uio.ifi.tc.model.pojo.ResumableUploads;
import no.uio.ifi.tc.model.pojo.TSDFileAPIResponse;
import no.uio.ifi.tc.model.pojo.TSDFiles;
import no.uio.ifi.tc.model.pojo.Token;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for proxying TSD File API requests. {@link AuthAspect} authenticates every
 * endpoint before it runs, and failures propagate as exceptions to the problem advice.
 */
@Slf4j
@RestController
public class ProxyController {

  @Value("${tsd-auth.token-type}")
  private String tokenType;

  @Value("${tsd-auth.oidc-provider-name}")
  private String oidcType;

  @Value("${tsd.app-id}")
  private String tsdAppId;

  @Value("${tsd.app-out-id}")
  private String tsdAppOutId;

  @Autowired private TSDFileAPIClient tsdFileAPIClient;

  @Autowired private HttpServletRequest request;

  /**
   * Streams the file to the TSD File API.
   *
   * @param inputStream Binary file stream.
   * @param fileName File name.
   * @param uploadId Upload ID.
   * @param chunk Chunk number.
   * @param fileSize File size.
   * @param md5 MD5 digest.
   * @return Response code and test for the operation.
   * @throws IOException In case of I/O error.
   */
  @PatchMapping("/stream/{fileName}")
  public ResponseEntity<?> stream(
      InputStream inputStream,
      @PathVariable("fileName") String fileName,
      @RequestParam(value = "uploadId", required = false) String uploadId,
      @RequestParam(value = "chunk", required = false) String chunk,
      @RequestParam(value = "fileSize", required = false) String fileSize,
      @RequestParam(value = "md5", required = false) String md5,
      @RequestParam(value = "sha256", required = false) String sha256)
      throws IOException {
    boolean finalizing = StringUtils.hasLength(uploadId) && "end".equalsIgnoreCase(chunk);
    if (!finalizing && md5 == null) {
      throw new ProxyException(ErrorCode.INVALID_REQUEST, "Chunk upload without an md5");
    }
    String token = tsdToken();

    if (finalizing) {
      return ResponseEntity.ok(
          requireSuccess(
              tsdFileAPIClient.finalizeResumableUpload(token, tsdAppId, uploadId), "finalize"));
    }

    byte[] chunkBytes = inputStream.readAllBytes();
    Chunk response;
    if (!StringUtils.hasLength(uploadId)) {
      response = tsdFileAPIClient.initializeResumableUpload(token, tsdAppId, chunkBytes, fileName);
    } else {
      long chunkNumber;
      try {
        chunkNumber = Long.parseLong(chunk);
      } catch (NumberFormatException e) {
        throw new ProxyException(ErrorCode.INVALID_REQUEST, "Chunk number is not a number", e);
      }
      response = tsdFileAPIClient.uploadChunk(token, tsdAppId, chunkNumber, chunkBytes, uploadId);
    }
    requireSuccess(response, "chunk upload");
    return validateChunkChecksum(token, response, md5);
  }

  private ResponseEntity<Chunk> validateChunkChecksum(String token, Chunk response, String md5)
      throws IOException {
    ResumableUpload resumableUpload =
        tsdFileAPIClient
            .getResumableUpload(token, tsdAppId, response.getId())
            .orElseThrow(
                () ->
                    new ProxyException(
                        ErrorCode.UPSTREAM_FAILED,
                        "TSD lists no resumable upload " + response.getId()));
    if (!md5.equalsIgnoreCase(resumableUpload.getMd5Sum())) {
      try {
        tsdFileAPIClient.deleteResumableUpload(token, tsdAppId, response.getId());
      } catch (IOException e) {
        log.error(
            "Could not discard resumable upload {} after a checksum mismatch", response.getId(), e);
      }
      throw new ProxyException(
          ErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch on upload " + response.getId());
    }
    return ResponseEntity.ok(response);
  }

  /**
   * Downloads a file from the outbox.
   *
   * @param response HTTP response the file is written to.
   * @param fileName File name.
   * @throws IOException In case of I/O error.
   */
  @NoEgaAuth
  @GetMapping("/stream/{fileName}")
  public void stream(HttpServletResponse response, @PathVariable("fileName") String fileName)
      throws IOException {
    String token = tsdToken();
    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(fileName).build().toString());
    // downloadFile copies only on a 2xx, so a refused download has written nothing and the
    // problem advice can still answer it.
    requireFound(
        tsdFileAPIClient.downloadFile(token, tsdAppOutId, fileName, response.getOutputStream()),
        "download");
  }

  /**
   * Lists uploaded files.
   *
   * @param page Page number.
   * @param perPage Items per page.
   * @return List of uploaded files.
   * @throws IOException In case of I/O error.
   */
  @GetMapping("/files")
  public ResponseEntity<TSDFiles> getInboxFiles(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "per_page", defaultValue = "50000") int perPage)
      throws IOException {
    return listFiles(tsdAppId, page, perPage);
  }

  /**
   * Lists exported files, which needs no EGA credentials.
   *
   * @param page Page number.
   * @param perPage Items per page.
   * @return List of exported files.
   * @throws IOException In case of I/O error.
   */
  @NoEgaAuth
  @GetMapping(value = "/files", params = "inbox=false")
  public ResponseEntity<TSDFiles> getOutboxFiles(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "per_page", defaultValue = "50000") int perPage)
      throws IOException {
    return listFiles(tsdAppOutId, page, perPage);
  }

  private ResponseEntity<TSDFiles> listFiles(String appId, int page, int perPage)
      throws IOException {
    TSDFiles tsdFiles = tsdFileAPIClient.listFiles(tsdToken(), appId, page, perPage);
    // TSD creates a user's folder on the first upload, so a 404 here means "not yet", and the
    // client
    // decides what to make of it. See ELIXIR-NO/FEGA-Norway#913.
    if (tsdFiles.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
      throw new ProxyException(ErrorCode.FOLDER_NOT_FOUND, "TSD listing: folder not found");
    }
    requireSuccess(tsdFiles, "listing");
    if (tsdFiles.getFiles() == null) {
      throw new ProxyException(ErrorCode.UPSTREAM_FAILED, "TSD listing has no files array");
    }
    log.info(
        "Files returned: {}, page: {}, perPage: {}", tsdFiles.getFiles().size(), page, perPage);
    return ResponseEntity.ok(tsdFiles);
  }

  /**
   * Deletes uploaded file.
   *
   * @param fileName File name.
   * @return Response code and text for the operation.
   * @throws IOException In case of I/O error.
   */
  @DeleteMapping("/files")
  public ResponseEntity<Message> deleteFile(@RequestParam(value = "fileName") String fileName)
      throws IOException {
    Message deleteResponse = tsdFileAPIClient.deleteFile(tsdToken(), tsdAppId, fileName);
    requireFound(deleteResponse, "delete");
    return ResponseEntity.status(deleteResponse.getStatusCode()).body(deleteResponse);
  }

  /**
   * Lists resumable uploads.
   *
   * @param uploadId Upload ID.
   * @return List of resumable uploads.
   * @throws IOException In case of I/O error.
   */
  @GetMapping("/resumables")
  public ResponseEntity<?> getResumables(
      @RequestParam(value = "uploadId", required = false) String uploadId) throws IOException {
    String token = tsdToken();
    if (!StringUtils.hasLength(uploadId)) {
      ResumableUploads resumableUploads = tsdFileAPIClient.listResumableUploads(token, tsdAppId);
      return ResponseEntity.ok(requireSuccess(resumableUploads, "resumables listing"));
    }
    return ResponseEntity.ok(tsdFileAPIClient.getResumableUpload(token, tsdAppId, uploadId));
  }

  /**
   * Deletes resumable upload.
   *
   * @param uploadId Upload ID.
   * @return Response code and text for the operation.
   * @throws IOException In case of I/O error.
   */
  @DeleteMapping("/resumables")
  public ResponseEntity<Message> deleteResumable(@RequestParam(value = "uploadId") String uploadId)
      throws IOException {
    return ResponseEntity.ok(
        requireSuccess(
            tsdFileAPIClient.deleteResumableUpload(tsdToken(), tsdAppId, uploadId),
            "resumable delete"));
  }

  /**
   * Notifies the proxy that a direct upload (-b mode) has completed. Triggers the RabbitMQ inbox
   * message publication for the SDA pipeline.
   *
   * @param fileName Uploaded file name.
   * @param fileSize Total file size in bytes.
   * @param sha256 SHA-256 checksum of the entire file.
   * @return 200 OK on success.
   */
  @PostMapping("/stream/{fileName}/notify")
  public ResponseEntity<Void> notifyUploadComplete(
      @PathVariable("fileName") String fileName,
      @RequestParam("fileSize") String fileSize,
      @RequestParam("sha256") String sha256) {

    if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
      throw new ProxyException(ErrorCode.INVALID_REQUEST, "Invalid file name");
    }
    long parsedFileSize;
    try {
      parsedFileSize = Long.parseLong(fileSize);
    } catch (NumberFormatException e) {
      throw new ProxyException(ErrorCode.INVALID_REQUEST, "fileSize is not a number", e);
    }
    if (parsedFileSize <= 0) {
      throw new ProxyException(ErrorCode.INVALID_REQUEST, "fileSize is not positive");
    }
    if (sha256 == null || !sha256.matches("^[a-fA-F0-9]{64}$")) {
      throw new ProxyException(ErrorCode.INVALID_REQUEST, "sha256 is not 64 hex characters");
    }

    request.setAttribute(ProcessArgumentsAspect.FILE_NAME, fileName);
    request.setAttribute(ProcessArgumentsAspect.FILE_SIZE, fileSize);
    request.setAttribute(ProcessArgumentsAspect.SHA256, sha256);
    request.setAttribute(ProcessArgumentsAspect.CHUNK, "end");

    return ResponseEntity.ok().build();
  }

  private String tsdToken() throws IOException {
    Token token =
        tsdFileAPIClient.getToken(
            tokenType, oidcType, (String) request.getAttribute(AuthAspect.ELIXIR_AAI_TOKEN));
    requireSuccess(token, "token exchange");
    if (!StringUtils.hasText(token.getToken())) {
      throw new ProxyException(ErrorCode.UPSTREAM_FAILED, "TSD token exchange returned no token");
    }
    return token.getToken();
  }

  private static <T extends TSDFileAPIResponse> T requireFound(T response, String call) {
    if (response.getStatusCode() == HttpStatus.NOT_FOUND.value()) {
      throw new ProxyException(ErrorCode.FILE_NOT_FOUND, "TSD " + call + ": file not found");
    }
    return requireSuccess(response, call);
  }

  private static <T extends TSDFileAPIResponse> T requireSuccess(T response, String call) {
    if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
      throw new ProxyException(
          ErrorCode.UPSTREAM_FAILED,
          "TSD " + call + " answered " + response.getStatusCode() + " " + response.getStatusText());
    }
    return response;
  }
}
