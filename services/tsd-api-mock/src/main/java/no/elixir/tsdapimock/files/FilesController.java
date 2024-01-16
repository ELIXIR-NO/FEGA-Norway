package no.elixir.tsdapimock.files;

import java.io.InputStream;
import no.elixir.tsdapimock.exceptions.CredentialsMismatchException;
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
}
