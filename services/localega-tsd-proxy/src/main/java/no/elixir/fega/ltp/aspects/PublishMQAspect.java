package no.elixir.fega.ltp.aspects;

import static no.elixir.fega.ltp.aspects.ProcessArgumentsAspect.*;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import no.elixir.fega.ltp.dto.EncryptedIntegrity;
import no.elixir.fega.ltp.dto.FileDescriptor;
import no.elixir.fega.ltp.dto.Operation;
import no.uio.ifi.tc.model.pojo.TSDFileAPIResponse;
import org.apache.http.entity.ContentType;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** AOP aspect that publishes MQ messages. */
@Slf4j
@Aspect
@Order(4)
@Component
public class PublishMQAspect {

  private final HttpServletRequest request;

  private final Gson gson;

  private final RabbitTemplate rabbitTemplate;

  @Value("${tsd.project}")
  private String tsdProjectId;

  @Value("${tsd.inbox-location}")
  private String tsdInboxLocation;

  @Value("${mq.cega.exchange}")
  private String exchange;

  @Value("${mq.cega.routing-key}")
  private String routingKey;

  @Autowired
  public PublishMQAspect(HttpServletRequest request, Gson gson, RabbitTemplate rabbitTemplate) {
    this.request = request;
    this.gson = gson;
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Publishes <code>FileDescriptor</code> to the MQ upon file uploading.
   *
   * @param result Object returned by the proxied method.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  @AfterReturning(
      pointcut =
          "execution(@org.springframework.web.bind.annotation.PatchMapping public * no.elixir.fega.ltp.controllers.rest.ProxyController.stream(..))",
      returning = "result")
  public void publishUpload(Object result) {
    ResponseEntity genericResponseEntity = (ResponseEntity) result;
    if (!String.valueOf(Objects.requireNonNull(genericResponseEntity).getStatusCode())
        .startsWith("20")) {
      log.error(String.valueOf(genericResponseEntity.getStatusCode()));
      log.error(String.valueOf(genericResponseEntity.getBody()));
      return;
    }
    ResponseEntity<TSDFileAPIResponse> tsdResponseEntity =
        (ResponseEntity<TSDFileAPIResponse>) result;
    TSDFileAPIResponse body = tsdResponseEntity.getBody();
    if (!String.valueOf(Objects.requireNonNull(body).getStatusCode()).startsWith("20")) {
      log.error(String.valueOf(body.getStatusCode()));
      log.error(String.valueOf(body.getStatusText()));
      return;
    }

    if (!"end".equalsIgnoreCase(String.valueOf(request.getAttribute(CHUNK)))) {
      return;
    }

    FileDescriptor fileDescriptor = new FileDescriptor();
    fileDescriptor.setUser(request.getAttribute(EGA_USERNAME).toString());
    String fileName = request.getAttribute(FILE_NAME).toString();
    fileDescriptor.setFilePath(
        String.format(tsdInboxLocation, tsdProjectId, request.getAttribute(ELIXIR_ID).toString())
            + fileName); // absolute path to the file
    fileDescriptor.setFileSize(Long.parseLong(request.getAttribute(FILE_SIZE).toString()));
    fileDescriptor.setFileLastModified(System.currentTimeMillis() / 1000);
    fileDescriptor.setOperation(Operation.UPLOAD.name().toLowerCase());
    fileDescriptor.setEncryptedIntegrity(
        new EncryptedIntegrity[] {
          new EncryptedIntegrity(SHA256.toLowerCase(), request.getAttribute(SHA256).toString())
        });
    publishMessage(fileDescriptor);
  }

  /**
   * Publishes <code>FileDescriptor</code> to the MQ upon file removal.
   *
   * @param result Object returned by the proxied method.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  @AfterReturning(
      pointcut =
          "execution(public * no.elixir.fega.ltp.controllers.rest.ProxyController.deleteFile(..))",
      returning = "result")
  public void publishRemove(Object result) {
    ResponseEntity genericResponseEntity = (ResponseEntity) result;
    if (!String.valueOf(Objects.requireNonNull(genericResponseEntity).getStatusCode())
        .startsWith("20")) {
      log.error(String.valueOf(genericResponseEntity.getStatusCode()));
      log.error(String.valueOf(genericResponseEntity.getBody()));
      return;
    }
    ResponseEntity<TSDFileAPIResponse> tsdResponseEntity =
        (ResponseEntity<TSDFileAPIResponse>) result;
    TSDFileAPIResponse body = tsdResponseEntity.getBody();
    if (!String.valueOf(Objects.requireNonNull(body).getStatusCode()).startsWith("20")) {
      log.error(String.valueOf(body.getStatusCode()));
      log.error(String.valueOf(body.getStatusText()));
      return;
    }

    FileDescriptor fileDescriptor = new FileDescriptor();
    fileDescriptor.setUser(request.getAttribute(EGA_USERNAME).toString());
    String fileName = request.getAttribute(FILE_NAME).toString();
    fileDescriptor.setFilePath(
        String.format(tsdInboxLocation, tsdProjectId, request.getAttribute(ELIXIR_ID).toString())
            + fileName);
    fileDescriptor.setOperation(Operation.REMOVE.name().toLowerCase());
    publishMessage(fileDescriptor);
  }

  private void publishMessage(FileDescriptor fileDescriptor) {
    String json = gson.toJson(fileDescriptor);
    rabbitTemplate.convertAndSend(
        exchange,
        routingKey,
        json,
        m -> {
          m.getMessageProperties().setContentType(ContentType.APPLICATION_JSON.getMimeType());
          m.getMessageProperties().setCorrelationId(UUID.randomUUID().toString());
          return m;
        });
    log.info(
        "Message published to {} exchange with routing key {}: {}", exchange, routingKey, json);
  }
}
