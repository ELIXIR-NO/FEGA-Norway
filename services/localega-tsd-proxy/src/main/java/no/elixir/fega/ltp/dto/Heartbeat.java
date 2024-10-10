package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import lombok.Data;
import lombok.Getter;

@Data
public class Heartbeat {

  @JsonProperty private Status status;
  @JsonProperty private String description;
  @JsonProperty private Collection<Component> queues;
  @JsonProperty private Collection<Component> services;

  public Heartbeat() {
    this.queues = new ArrayList<>();
    this.services = new ArrayList<>();
  }

  @Data
  public static class Component {

    @JsonProperty private String name;
    @JsonProperty private Status status;
    @JsonProperty private LocalDateTime last_seen_ok = null;
    @JsonProperty private LocalDateTime last_seen_failed = null;

    public enum Status {
      OK("ok"),
      NOT_OK("not_ok");

      public final String status;

      Status(String status) {
        this.status = status;
      }

      // Convert from String to Enum
      public static Status fromString(String status) {
        for (Status s : Status.values()) {
          if (s.status.equalsIgnoreCase(status)) {
            return s;
          }
        }
        throw new IllegalArgumentException("Unknown status: " + status);
      }

      @Override
      public String toString() {
        return this.status;
      }
    }
  }

  @Getter
  public enum Status {
    ALL_OK("All services and queues are up and running"),
    MISSING_SERVICES("Missing one or more services"),
    MISSING_QUEUES("Missing one or more queues"),
    MISSING_SERVICES_AND_QUEUES("Missing one or more services and queues");

    public final String description;

    Status(String description) {
      this.description = description;
    }
  }
}
