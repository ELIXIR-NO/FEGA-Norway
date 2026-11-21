package no.elixir.fega.ltp.controllers.rest;

import no.elixir.fega.ltp.dto.FegaExportRequestDto;
import no.elixir.fega.ltp.dto.GdiExportRequestDto;
import no.elixir.fega.ltp.dto.GenericResponse;
import no.elixir.fega.ltp.services.ExportRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Export requests; failures propagate to the problem advice. */
@RestController
@RequestMapping("/export")
public class ExportRequestController {

  private static final GenericResponse COMPLETED =
      new GenericResponse("Export request completed successfully");

  private final ExportRequestService exportRequestService;

  @Autowired
  public ExportRequestController(ExportRequestService exportRequestService) {
    this.exportRequestService = exportRequestService;
  }

  @PostMapping("/gdi")
  public ResponseEntity<GenericResponse> exportRequest(@RequestBody GdiExportRequestDto body) {
    exportRequestService.exportRequestGDI(body);
    return ResponseEntity.ok(COMPLETED);
  }

  @PostMapping("/fega")
  public ResponseEntity<GenericResponse> exportRequestFega(@RequestBody FegaExportRequestDto body) {
    exportRequestService.exportRequestFEGA(body);
    return ResponseEntity.ok(COMPLETED);
  }
}
