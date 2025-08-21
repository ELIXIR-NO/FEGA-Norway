package no.elixir.fega.ltp.services;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import no.elixir.clearinghouse.model.Visa;
import no.elixir.fega.ltp.dto.ExportRequestDto;
import no.elixir.fega.ltp.exceptions.GenericException;
import no.elixir.fega.ltp.models.DOAExportRequest;
import org.apache.http.entity.ContentType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ExportRequestService {

  private final TokenService tokenService;
  private final RabbitTemplate tsdRabbitTemplate;

  @Value("${mq.tsd.exchange}")
  private String exchange;

  @Value("${mq.tsd.export-requests-routing-key}")
  private String routingKey;

  @Autowired
  public ExportRequestService(TokenService tokenService, RabbitTemplate tsdRabbitTemplate) {
    this.tokenService = tokenService;
    this.tsdRabbitTemplate = tsdRabbitTemplate;
  }

  public void exportRequest(ExportRequestDto exportRequestDto)
      throws GenericException, IllegalArgumentException {

    String subject = tokenService.getSubject(exportRequestDto.getAccessToken());
    List<Visa> controlledAccessGrantsVisas =
        tokenService.getControlledAccessGrantsVisas(exportRequestDto.getAccessToken());
    log.info(
        "Elixir user {} authenticated and provided following valid GA4GH Visas: {}",
        subject,
        controlledAccessGrantsVisas);

    Set<Visa> collect =
        controlledAccessGrantsVisas.stream()
            .filter(
                visa -> {
                  String escapedId = Pattern.quote(exportRequestDto.getId());
                  return visa.getValue().matches(".*" + escapedId + ".*");
                })
            .collect(Collectors.toSet());

    if (collect.isEmpty()) {
      log.info(
          "No visas found for user {}. Requested to export {} {}",
          subject,
          exportRequestDto.getId(),
          exportRequestDto.getType());
      throw new GenericException(HttpStatus.BAD_REQUEST, "No visas found");
    }

    collect.stream()
        .findFirst()
        .ifPresent(
            (visa -> {
              log.info(
                  "Found {} visa(s). Using the first visa to make the request.", collect.size());

              exportRequestDto.setJwtToken(visa.getRawToken());
              DOAExportRequest message = DOAExportRequest.fromExportRequestDto(exportRequestDto);
              tsdRabbitTemplate.convertAndSend(
                  exchange,
                  routingKey,
                  message,
                  m -> {
                    m.getMessageProperties()
                        .setContentType(ContentType.APPLICATION_JSON.getMimeType());
                    m.getMessageProperties().setCorrelationId(UUID.randomUUID().toString());
                    return m;
                  });
              log.info(
                  "Export request: {} | Exchange: {} | Routing-key: {}",
                  message,
                  exchange,
                  routingKey);
            }));
  }
}
