package no.elixir.tsdapimock.endpoints.egaout;

import java.io.File;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.endpoints.egaout.dto.OutboxFileListingDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/{project}/egaout/{userName}")
public class EgaoutFilesController {

  // Same text as the real TSD file API.
  private static final String NOT_FOUND = "Resource not found";

  private final EgaoutFilesService egaoutFilesService;

  @Autowired
  public EgaoutFilesController(EgaoutFilesService egaoutFilesService) {
    this.egaoutFilesService = egaoutFilesService;
  }

  @GetMapping(value = "/files", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> get(
      @PathVariable String project,
      @PathVariable String userName,
      @RequestHeader("Authorization") String authorizationHeader) {
    try {
      OutboxFileListingDto outboxFileListingDto =
          egaoutFilesService.listOutboxFiles(authorizationHeader, project, userName);
      if (outboxFileListingDto == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(NOT_FOUND);
      }
      return ResponseEntity.status(HttpStatus.OK)
          .contentType(MediaType.APPLICATION_JSON)
          .body(outboxFileListingDto);
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
  }

  /** Serves a single file from the user's outbox, the way the proxy downloads an export. */
  @GetMapping("/files/{fileName}")
  public ResponseEntity<?> download(
      @PathVariable String project,
      @PathVariable String userName,
      @PathVariable String fileName,
      @RequestHeader("Authorization") String authorizationHeader) {
    try {
      File file =
          egaoutFilesService.getOutboxFile(authorizationHeader, project, userName, fileName);
      if (file == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(NOT_FOUND);
      }
      return ResponseEntity.status(HttpStatus.OK)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .body(new FileSystemResource(file));
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
  }
}
