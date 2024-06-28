package no.elixir.tsdapimock.ega;

import no.elixir.tsdapimock.exceptions.CredentialsMismatchException;
import no.elixir.tsdapimock.exceptions.FileProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/{project}/ega/{userName}")
public class EgaFilesController {
  private final EgaFilesService egaFilesService;

  @Autowired
  public EgaFilesController(EgaFilesService egaFilesService) {
    this.egaFilesService = egaFilesService;
  }

  @GetMapping(value = "/resumables", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> getResumableUploads(
      @PathVariable String project,
      @PathVariable String userName,
      @RequestHeader("Authorization") String authorization) {
    try {
      var response = egaFilesService.getResumableUploads(project, userName, authorization);
      return ResponseEntity.ok(response);
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
  }

  @PatchMapping(
      value = "/files/{fileName}",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> handleResumableUpload(
      @PathVariable String project,
      @PathVariable String userName,
      @PathVariable String fileName,
      @RequestHeader("Authorization") String authorizationHeader,
      @RequestParam("chunk") String chunk,
      @RequestParam(value = "id", required = false) String id,
      @RequestBody(required = false) byte[] content) {
    try {
      var response =
          egaFilesService.handleResumableUpload(
              project, fileName, authorizationHeader, userName, chunk, id, content);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (FileProcessingException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }
}
