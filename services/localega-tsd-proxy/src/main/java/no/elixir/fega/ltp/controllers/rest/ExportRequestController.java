package no.elixir.fega.ltp.controllers.rest;

import lombok.extern.slf4j.Slf4j;
import no.elixir.fega.ltp.dto.FegaExportRequestDto;
import no.elixir.fega.ltp.dto.GdiExportRequestDto;
import no.elixir.fega.ltp.dto.GenericResponse;
import no.elixir.fega.ltp.exceptions.GenericException;
import no.elixir.fega.ltp.services.ExportRequestService;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/export")
public class ExportRequestController {

  private final ExportRequestService exportRequestService;

  @Autowired
  public ExportRequestController(ExportRequestService exportRequestService) {
    this.exportRequestService = exportRequestService;
  }

  @PostMapping("/gdi")
  public ResponseEntity<GenericResponse> exportRequest(@RequestBody GdiExportRequestDto body) {
    try {
      exportRequestService.exportRequestGDI(body);
      return ResponseEntity.status(HttpStatus.OK)
          .body(new GenericResponse("Export request completed successfully"));
    } catch (GenericException e) {
      log.error("Export request failed for user: {}", e.getMessage(), e);
      return ResponseEntity.status(e.getHttpStatus()).body(new GenericResponse(e.getMessage()));
    } catch (IllegalArgumentException e) {
      log.error("Invalid export request: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new GenericResponse(e.getMessage()));
    } catch (Exception e) {
      log.error("Unexpected error during export request", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new GenericResponse("An unexpected error occurred"));
    }
  }

  @PostMapping("/fega")
  public ResponseEntity<GenericResponse> exportRequestFega(@RequestBody FegaExportRequestDto body) {
    try {
      exportRequestService.exportRequestFEGA(body);
      return ResponseEntity.status(HttpStatus.OK)
          .body(new GenericResponse("Export request completed successfully"));
    } catch (GenericException e) {
      log.error("FEGA export request failed: {}", e.getMessage(), e);
      return ResponseEntity.status(e.getHttpStatus()).body(new GenericResponse(e.getMessage()));
    } catch (IllegalArgumentException e) {
      log.error("Invalid FEGA export request: {}", e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new GenericResponse(e.getMessage()));
    } catch (AmqpException e) {
      log.error("Failed to send message to RabbitMQ", e);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new GenericResponse("Message queue service unavailable"));
    } catch (Exception e) {
      log.error("Unexpected error during FEGA export request", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new GenericResponse("An unexpected error occurred"));
    }
  }
}
