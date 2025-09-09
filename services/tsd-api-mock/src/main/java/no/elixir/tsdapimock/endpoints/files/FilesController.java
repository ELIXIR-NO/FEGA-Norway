package no.elixir.tsdapimock.endpoints.files;

import jakarta.validation.constraints.NotBlank;
import java.io.InputStream;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.core.exceptions.FailedResourceCreationException;
import no.elixir.tsdapimock.core.exceptions.FileProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/{project}/files/")
public class FilesController {
  private final FilesService filesService;

  @Autowired
  public FilesController(FilesService filesService) {
    this.filesService = filesService;
  }

  @PutMapping(
      value = "/stream",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> upload(
      @PathVariable String project,
      @RequestHeader("Authorization") String authorizationHeader,
      @RequestHeader("filename") String fileName,
      InputStream fileStream) {
    try {
      var response = filesService.upload(project, authorizationHeader, fileName, fileStream);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
  }

  @PutMapping(
      value = "/folder",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> createFolder(
      @PathVariable String project,
      @RequestHeader("Authorization") String authorizationHeader,
      @RequestParam("name") @NotBlank String folderName) {
    try {
      var response = filesService.createFolder(project, authorizationHeader, folderName);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (FailedResourceCreationException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }

  @GetMapping(value = "/resumables", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getResumableUploads(
      @PathVariable String project, @RequestHeader("Authorization") String authorizationHeader) {
    try {
      var response = filesService.getResumableUploads(project, authorizationHeader);
      return ResponseEntity.ok(response);
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
  }

  @PatchMapping(
      value = "/stream/{filename}",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> handleResumableUpload(
      @PathVariable String project,
      @PathVariable String filename,
      @RequestHeader("Authorization") String authorizationHeader,
      @RequestParam(value = "filename", required = false) String fileName,
      @RequestParam("chunk") String chunk,
      @RequestParam(value = "id", required = false) String id,
      @RequestBody byte[] content) {
    try {
      var response =
          filesService.handleResumableUpload(
              project, filename, authorizationHeader, fileName, chunk, id, content);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (FileProcessingException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }

  @DeleteMapping(value = "/resumables/{filename}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> deleteResumableUpload(
      @PathVariable("project") String project,
      @RequestHeader("Authorization") String authorization,
      @PathVariable("filename") String fileName,
      @RequestParam(value = "id") String id) {
    try {
      var response = filesService.deleteResumableUpload(project, authorization, fileName, id);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (FileProcessingException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }
}
