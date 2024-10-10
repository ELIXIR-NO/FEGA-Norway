package no.elixir.fega.ltp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import no.elixir.fega.ltp.dto.Heartbeat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HeartbeatService {

  private final RedisTemplate<String, String> redisTemplate;

  @Value("${heartbeat.ok_if_ok_is_after_failed_and_diff_in_minutes_ge}")
  private int okAfterFailDiffMinGE;

  @Value("${heartbeat.not_ok_if_failed_is_after_ok_and_diff_in_minutes_ge}")
  private int notOkFailedAfterOkMinGE;

  public HeartbeatService(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public Heartbeat getHeartbeat() throws JsonProcessingException {

    Heartbeat heartbeat = new Heartbeat();

    heartbeat.setServices(processKeys("service:*").values());
    heartbeat.setQueues(processKeys("queue:*").values());

    boolean servicesOk = areAllComponentsOk(heartbeat.getServices());
    boolean queuesOk = areAllComponentsOk(heartbeat.getQueues());

    // Set the default status
    Heartbeat.Status status = Heartbeat.Status.MISSING_SERVICES_AND_QUEUES;

    // Update the status based on the conditions
    if (queuesOk && servicesOk) {
      status = Heartbeat.Status.ALL_OK;
    } else if (queuesOk) {
      status = Heartbeat.Status.MISSING_SERVICES;
    } else if (servicesOk) {
      status = Heartbeat.Status.MISSING_QUEUES;
    }

    // Set the final status and description
    heartbeat.setStatus(status);
    heartbeat.setDescription(status.getDescription());

    return heartbeat;
  }

  private Map<String, Heartbeat.Component> processKeys(String pattern) {
    Set<String> keys = redisTemplate.keys(pattern);
    Map<String, Heartbeat.Component> records = new HashMap<>();
    if (keys != null) {
      for (String key : keys) {
        evaluateComponentStatus(key, records);
      }
    }
    return records;
  }

  private void evaluateComponentStatus(String key, Map<String, Heartbeat.Component> records) {

    int NAME = 1, STATUS = 2;
    String[] data = key.split(":");
    assert data.length == 3;

    // Retrieve the value (timestamp) from Redis
    String value = redisTemplate.opsForValue().get(key);

    try {
      var status = Heartbeat.Component.Status.fromString(data[STATUS]);
      LocalDateTime timestamp = null;
      if (value != null) {
        // Parse the time to local date time
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
        timestamp = LocalDateTime.parse(value, formatter);
      }
      // If this component doesn't exist in the records yet, create it
      if (!records.containsKey(data[NAME])) {
        records.put(data[NAME], new Heartbeat.Component());
      }
      Heartbeat.Component component = records.get(data[NAME]);
      // Update the component name
      component.setName(data[NAME]);
      // Set the last seen ok or failed based on the current status
      if (status == Heartbeat.Component.Status.OK) {
        component.setLast_seen_ok(timestamp);
      } else if (status == Heartbeat.Component.Status.NOT_OK) {
        component.setLast_seen_failed(timestamp);
      }
      // Now process the final component status based on the rules
      evaluateFinalStatusOfComponent(status, component);
    } catch (IllegalArgumentException e) {
      log.info("Unknown heartbeat status: {}", data[STATUS]);
    }
  }

  private void evaluateFinalStatusOfComponent(
      Heartbeat.Component.Status currentStatus, Heartbeat.Component component) {

    LocalDateTime lastSeenOk = component.getLast_seen_ok();
    LocalDateTime lastSeenFailed = component.getLast_seen_failed();

    if (lastSeenOk != null && lastSeenFailed != null) {
      // Calculate the difference between the timestamps
      Duration difference = Duration.between(lastSeenFailed, lastSeenOk);
      if (lastSeenOk.isAfter(lastSeenFailed) && difference.toMinutes() >= okAfterFailDiffMinGE) {
        // If last seen OK is more recent than last seen failed and
        // the difference is more than 10 minutes
        component.setStatus(Heartbeat.Component.Status.OK);
      } else if (lastSeenFailed.isAfter(lastSeenOk)
          && difference.toMinutes() >= notOkFailedAfterOkMinGE) {
        // If last seen failed is more recent than last seen OK and
        // the difference is at least 3 minutes
        component.setStatus(Heartbeat.Component.Status.NOT_OK);
      } else {
        // If nothing meets the criteria fallback to the value given
        // by the heartbeat service
        component.setStatus(currentStatus);
      }
    } else if (lastSeenOk != null) {
      // If there is no last_seen_failed timestamp, consider it OK
      component.setStatus(Heartbeat.Component.Status.OK);
    } else if (lastSeenFailed != null) {
      // If there is no last_seen_ok timestamp, consider it NOT OK
      component.setStatus(Heartbeat.Component.Status.NOT_OK);
    } else {
      // If nothing is set fallback to the value given
      // by the heartbeat service
      component.setStatus(currentStatus);
    }
  }

  private boolean areAllComponentsOk(Collection<Heartbeat.Component> components) {
    for (Heartbeat.Component component : components) {
      if (component.getStatus() == Heartbeat.Component.Status.NOT_OK) {
        return false; // If any component is NOT_OK, return false
      }
    }
    return true; // If all components are OK, return true
  }
}
