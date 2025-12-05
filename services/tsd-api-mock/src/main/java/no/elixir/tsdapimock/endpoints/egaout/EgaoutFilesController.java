package no.elixir.tsdapimock.endpoints.egaout;

import no.elixir.tsdapimock.endpoints.egaout.dto.OutboxFileListingDto;
import no.elixir.tsdapimock.core.exceptions.CredentialsMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/{project}/egaout/{userName}")
public class EgaoutFilesController {

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
      return ResponseEntity.status(HttpStatus.OK)
          .contentType(MediaType.APPLICATION_JSON)
          .body(outboxFileListingDto);
    } catch (CredentialsMismatchException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
  }


  // FIXME: have download functionality

}
