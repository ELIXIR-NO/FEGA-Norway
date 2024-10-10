package no.elixir.fega.ltp.controllers.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import no.elixir.fega.ltp.dto.Heartbeat;
import no.elixir.fega.ltp.services.HeartbeatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeartbeatController {

  private final HeartbeatService heartbeatService;

  public HeartbeatController(HeartbeatService heartbeatService) {
    this.heartbeatService = heartbeatService;
  }

  @GetMapping("/heartbeat")
  public ResponseEntity<Heartbeat> getHeartbeatStatus() {
    try {
      Heartbeat heartbeat = heartbeatService.getHeartbeat();
      return ResponseEntity.status(
              heartbeat.getStatus() == Heartbeat.Status.ALL_OK
                  ? HttpStatus.OK
                  : HttpStatus.SERVICE_UNAVAILABLE)
          .body(heartbeat);
    } catch (JsonProcessingException e) {
      return ResponseEntity.internalServerError().build();
    }
  }
}
