package no.elixir.fega.ltp.services;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import no.elixir.clearinghouse.model.Visa;
import no.elixir.fega.ltp.dto.GdiExportRequestDto;
import no.elixir.fega.ltp.dto.FegaExportRequestDto;
import no.elixir.fega.ltp.exceptions.GenericException;
import no.elixir.fega.ltp.models.DOAExportRequest;
import org.apache.http.entity.ContentType;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

  public void exportRequestGDI(GdiExportRequestDto gdiExportRequestDto)
      throws GenericException, IllegalArgumentException {

    // Validate input
    if (gdiExportRequestDto == null) {
      throw new IllegalArgumentException("Export request cannot be null");
    }
    if (!StringUtils.hasText(gdiExportRequestDto.getAccessToken())) {
      throw new IllegalArgumentException("Access token cannot be null or empty");
    }

    String subject = tokenService.getSubject(gdiExportRequestDto.getAccessToken());
    List<Visa> controlledAccessGrantsVisas =
        tokenService.getControlledAccessGrantsVisas(gdiExportRequestDto.getAccessToken());

    log.info(
        "Elixir user {} authenticated and provided {} valid GA4GH Visa(s)",
        subject,
        controlledAccessGrantsVisas != null ? controlledAccessGrantsVisas.size() : 0);

    if (controlledAccessGrantsVisas == null || controlledAccessGrantsVisas.isEmpty()) {
      log.warn(
          "No visas found for user {}. Requested to export {} {}",
          subject,
          gdiExportRequestDto.getId(),
          gdiExportRequestDto.getType());
      throw new GenericException(HttpStatus.FORBIDDEN, "No valid visas found for this resource");
    }

    String escapedId = Pattern.quote(gdiExportRequestDto.getId());
    Set<Visa> matchingVisas =
        controlledAccessGrantsVisas.stream()
            .filter(visa -> visa.getValue().matches(".*" + escapedId + ".*"))
            .collect(Collectors.toSet());

    if (matchingVisas.isEmpty()) {
      log.warn(
          "No matching visas found for user {} and resource ID {}. User has {} visa(s) but none match.",
          subject,
          gdiExportRequestDto.getId(),
          controlledAccessGrantsVisas.size());
      throw new GenericException(
          HttpStatus.FORBIDDEN, "No valid visa found for the requested resource");
    }

    // Use the first matching visa (consider documenting why first is chosen)
    Visa selectedVisa = matchingVisas.stream().findFirst().orElseThrow();

    if (matchingVisas.size() > 1) {
      log.info(
          "Found {} matching visa(s) for resource {}. Using the first visa.",
          matchingVisas.size(),
          gdiExportRequestDto.getId());
    }

    DOAExportRequest message = DOAExportRequest.fromExportRequestDto(gdiExportRequestDto, selectedVisa.getRawToken());

    sendToRabbitMQ(message);

    log.info(
        "Export request sent successfully for user {} | Resource: {} | Type: {}",
        subject,
        gdiExportRequestDto.getId(),
        gdiExportRequestDto.getType());
  }

  public void exportRequestFEGA(FegaExportRequestDto fegaExportRequestDto) throws AmqpException {
    if (fegaExportRequestDto == null) {
      throw new IllegalArgumentException("Export request cannot be null");
    }

    DOAExportRequest message = DOAExportRequest.fromExportRequestDto(fegaExportRequestDto);
    sendToRabbitMQ(message);

    log.info(
        "FEGA export request sent successfully | Resource: {} | Type: {}",
        fegaExportRequestDto.getId(),
        fegaExportRequestDto.getType());
  }

  private void sendToRabbitMQ(DOAExportRequest message) throws AmqpException {
    tsdRabbitTemplate.convertAndSend(
        exchange,
        routingKey,
        message,
        m -> {
          m.getMessageProperties().setContentType(ContentType.APPLICATION_JSON.getMimeType());
          m.getMessageProperties().setCorrelationId(UUID.randomUUID().toString());
          return m;
        });

    log.debug(
        "Message sent | Exchange: {} | Routing-key: {} | Correlation-ID: {}",
        exchange,
        routingKey,
        message);
  }

}
