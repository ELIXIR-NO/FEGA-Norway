package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class HeartbeatStatus {

  @JsonProperty private List<QueueStatus> queuesStatus;
  @JsonProperty private List<ServiceStatus> servicesStatus;

  public HeartbeatStatus() {
    this.queuesStatus = new ArrayList<>();
    this.servicesStatus = new ArrayList<>();
  }

  // Inner class for Queue Status
  @Data
  public static class QueueStatus {
    @JsonProperty private String name;
    @JsonProperty private String status;
  }

  @Data
  public static class ServiceStatus {
    @JsonProperty private String name;
    @JsonProperty private String status;
  }
}