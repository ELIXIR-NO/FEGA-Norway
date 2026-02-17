package no.elixir.fega.ltp.aspects;

import static no.elixir.fega.ltp.aspects.ProcessArgumentsAspect.*;

import jakarta.servlet.http.HttpServletRequest;
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

  private final RabbitTemplate tsdRabbitTemplate;

  @Value("${tsd.project}")
  private String tsdProjectId;

  @Value("${tsd.inbox-path-format}")
  private String inboxPathFormat;

  @Value("${mq.tsd.exchange}")
  private String exchange;

  @Value("${mq.tsd.inbox-routing-key}")
  private String routingKey;

  @Autowired
  public PublishMQAspect(HttpServletRequest request, RabbitTemplate tsdRabbitTemplate) {
    this.request = request;
    this.tsdRabbitTemplate = tsdRabbitTemplate;
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
    if (!(result instanceof ResponseEntity)) {
      log.error("Unexpected result type: {}", result != null ? result.getClass() : "null");
      return;
    }
    ResponseEntity genericResponseEntity = (ResponseEntity) result;
    if (!String.valueOf(genericResponseEntity.getStatusCode()).startsWith("20")) {
      log.error(String.valueOf(genericResponseEntity.getStatusCode()));
      log.error(String.valueOf(genericResponseEntity.getBody()));
      return;
    }
    Object body = genericResponseEntity.getBody();
    if (!(body instanceof TSDFileAPIResponse tsdBody)) {
      log.error("Unexpected response body type: {}", body != null ? body.getClass() : "null");
      return;
    }
    if (!String.valueOf(tsdBody.getStatusCode()).startsWith("20")) {
      log.error(String.valueOf(tsdBody.getStatusCode()));
      log.error(String.valueOf(tsdBody.getStatusText()));
      return;
    }

    if (!"end".equalsIgnoreCase(String.valueOf(request.getAttribute(CHUNK)))) {
      return;
    }

    Object elixirId = request.getAttribute(ELIXIR_ID);
    Object fileName = request.getAttribute(FILE_NAME);
    Object fileSize = request.getAttribute(FILE_SIZE);
    Object sha256 = request.getAttribute(SHA256);
    if (elixirId == null || fileName == null || fileSize == null || sha256 == null) {
      log.error(
          "Missing required request attributes: ELIXIR_ID={}, FILE_NAME={}, FILE_SIZE={}, SHA256={}",
          elixirId, fileName, fileSize, sha256);
      return;
    }

    FileDescriptor fileDescriptor = new FileDescriptor();
    fileDescriptor.setUser(elixirId.toString());
    fileDescriptor.setFilePath(buildInboxPath(fileName.toString()));
    fileDescriptor.setFileSize(Long.parseLong(fileSize.toString()));
    fileDescriptor.setFileLastModified(System.currentTimeMillis() / 1000);
    fileDescriptor.setOperation(Operation.UPLOAD.name().toLowerCase());
    fileDescriptor.setEncryptedIntegrity(
        new EncryptedIntegrity[] {
          new EncryptedIntegrity(SHA256.toLowerCase(), sha256.toString())
        });
    publishMessage(fileDescriptor, Operation.UPLOAD.name().toLowerCase());
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
    if (!(result instanceof ResponseEntity)) {
      log.error("Unexpected result type: {}", result != null ? result.getClass() : "null");
      return;
    }
    ResponseEntity genericResponseEntity = (ResponseEntity) result;
    if (!String.valueOf(genericResponseEntity.getStatusCode()).startsWith("20")) {
      log.error(String.valueOf(genericResponseEntity.getStatusCode()));
      log.error(String.valueOf(genericResponseEntity.getBody()));
      return;
    }
    Object body = genericResponseEntity.getBody();
    if (!(body instanceof TSDFileAPIResponse tsdBody)) {
      log.error("Unexpected response body type: {}", body != null ? body.getClass() : "null");
      return;
    }
    if (!String.valueOf(tsdBody.getStatusCode()).startsWith("20")) {
      log.error(String.valueOf(tsdBody.getStatusCode()));
      log.error(String.valueOf(tsdBody.getStatusText()));
      return;
    }

    Object elixirId = request.getAttribute(ELIXIR_ID);
    Object fileName = request.getAttribute(FILE_NAME);
    if (elixirId == null || fileName == null) {
      log.error(
          "Missing required request attributes: ELIXIR_ID={}, FILE_NAME={}", elixirId, fileName);
      return;
    }

    FileDescriptor fileDescriptor = new FileDescriptor();
    fileDescriptor.setUser(elixirId.toString());
    fileDescriptor.setFilePath(buildInboxPath(fileName.toString()));
    fileDescriptor.setOperation(Operation.REMOVE.name().toLowerCase());
    publishMessage(fileDescriptor, Operation.REMOVE.name().toLowerCase());
  }

  /**
   * Builds the user-specific relative inbox path by formatting the inbox path format string with
   * the TSD project ID and the Elixir user ID, then appending the filename. The resulting path
   * follows the pattern: {@code /<tsd-project-id>-<elixir-user-id>/files/<fileName>}.
   *
   * @param fileName the name of the file to append to the inbox path.
   * @return the fully constructed inbox path for the given file.
   */
  private String buildInboxPath(String fileName) {
    Object elixirId = request.getAttribute(ELIXIR_ID);
    if (elixirId == null) {
      throw new IllegalStateException("ELIXIR_ID request attribute is not set");
    }
    return String.format(inboxPathFormat, tsdProjectId, elixirId.toString()) + fileName;
  }

  private void publishMessage(FileDescriptor fileDescriptor, String type) {
    tsdRabbitTemplate.convertAndSend(
        exchange,
        routingKey,
        fileDescriptor,
        m -> {
          m.getMessageProperties().setContentType(ContentType.APPLICATION_JSON.getMimeType());
          m.getMessageProperties().setCorrelationId(UUID.randomUUID().toString());
          return m;
        });
    log.info(
        "{} message published to {} exchange with routing key {}: {}",
        type,
        exchange,
        routingKey,
        fileDescriptor);
  }
}
