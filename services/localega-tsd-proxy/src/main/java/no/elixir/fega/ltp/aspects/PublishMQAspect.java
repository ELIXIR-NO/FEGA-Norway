package no.elixir.fega.ltp.aspects;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static no.elixir.fega.ltp.aspects.ProcessArgumentsAspect.*;

/**
 * AOP aspect that publishes MQ messages.
 */
@Slf4j
@Aspect
@Order(4)
@Component
public class PublishMQAspect {

    private final HttpServletRequest request;

    private final Gson gson;

    private final RabbitTemplate tsdRabbitTemplate;

    @Value("${tsd.project}")
    private String tsdProjectId;

    @Value("${tsd.inbox-location}")
    private String tsdInboxLocation;

    @Value("${mq.tsd.exchange}")
    private String exchange;

    @Value("${mq.tsd.inbox-routing-key}")
    private String routingKey;

    @Autowired
    public PublishMQAspect(HttpServletRequest request, Gson gson, RabbitTemplate tsdRabbitTemplate) {
        this.request = request;
        this.gson = gson;
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
        try {
            log.info("upload result: {}", result.toString());

            ResponseEntity genericResponseEntity = (ResponseEntity) result;
            if (!String.valueOf(Objects.requireNonNull(genericResponseEntity).getStatusCode())
                    .startsWith("20")) {
                log.error(String.valueOf("upload-TEST-1: " +genericResponseEntity.getStatusCode()));
                log.error(String.valueOf("upload-TEST-2: " +genericResponseEntity.getBody()));
                return;
            }
            log.info("upload - genericResponseEntity {} {} ", genericResponseEntity.getStatusCode(), genericResponseEntity.getBody());


            ResponseEntity<TSDFileAPIResponse> tsdResponseEntity =
                    (ResponseEntity<TSDFileAPIResponse>) result;
            TSDFileAPIResponse body = tsdResponseEntity.getBody();

            log.info("upload -  tsdResponseEntity {} {} ", tsdResponseEntity.getStatusCode(), tsdResponseEntity.getBody());

            if (!String.valueOf(Objects.requireNonNull(body).getStatusCode()).startsWith("20")) {
                log.error(String.valueOf("upload-TEST-3: " +body.getStatusCode()));
                log.error(String.valueOf("upload-TEST-4: " +body.getStatusText()));
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
                    new EncryptedIntegrity[]{
                            new EncryptedIntegrity(SHA256.toLowerCase(), request.getAttribute(SHA256).toString())
                    });
            publishMessage(fileDescriptor, Operation.UPLOAD.name().toLowerCase());
        } catch (Exception e) {
            log.error(String.valueOf("upload-TEST-5: " +e.getMessage()));
            log.error(String.valueOf("upload-TEST-6: " +Arrays.toString(e.getStackTrace())));
        }
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
        try {
            log.info("remove result: {}", result.toString());
            ResponseEntity genericResponseEntity = (ResponseEntity) result;
            if (!String.valueOf(Objects.requireNonNull(genericResponseEntity).getStatusCode())
                    .startsWith("20")) {
                log.error(String.valueOf("remove-TEST-1: " + genericResponseEntity.getStatusCode()));
                log.error(String.valueOf("remove-TEST-2: " + genericResponseEntity.getBody()));
                return;
            }
            log.info("remove - genericResponseEntity {} {} ", genericResponseEntity.getStatusCode(), genericResponseEntity.getBody());

            ResponseEntity<TSDFileAPIResponse> tsdResponseEntity =
                    (ResponseEntity<TSDFileAPIResponse>) result;
            TSDFileAPIResponse body = tsdResponseEntity.getBody();
            if (!String.valueOf(Objects.requireNonNull(body).getStatusCode()).startsWith("20")) {
                log.error(String.valueOf("remove-TEST-3: " + body.getStatusCode()));
                log.error(String.valueOf("remove-TEST-4: " + body.getStatusText()));
                return;
            }
            log.info("remove - tsdResponseEntity {} {} ", tsdResponseEntity.getStatusCode(), tsdResponseEntity.getBody());


            FileDescriptor fileDescriptor = new FileDescriptor();
            fileDescriptor.setUser(request.getAttribute(EGA_USERNAME).toString());
            String fileName = request.getAttribute(FILE_NAME).toString();
            fileDescriptor.setFilePath(
                    String.format(tsdInboxLocation, tsdProjectId, request.getAttribute(ELIXIR_ID).toString())
                            + fileName);
            fileDescriptor.setOperation(Operation.REMOVE.name().toLowerCase());
            publishMessage(fileDescriptor, Operation.REMOVE.name().toLowerCase());
        } catch (Exception e) {
            log.error(String.valueOf("remove-TEST-5: " +e.getMessage()));
            log.error(String.valueOf("remove-TEST-6: " + Arrays.toString(e.getStackTrace())));
        }
    }

    private void publishMessage(FileDescriptor fileDescriptor, String type) {
        String json = gson.toJson(fileDescriptor);
        tsdRabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                json,
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
                json);
    }
}
