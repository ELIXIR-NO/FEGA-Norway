package no.elixir.fega.ltp.services;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import no.elixir.fega.ltp.dto.ExportRequest;
import no.elixir.fega.ltp.exceptions.GenericException;
import no.uio.ifi.clearinghouse.model.Visa;
import no.uio.ifi.clearinghouse.model.VisaType;
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

  @Value("${mq.tsd.routing-key}")
  private String routingKey;

  @Autowired
  public ExportRequestService(TokenService tokenService, RabbitTemplate tsdRabbitTemplate) {
    this.tokenService = tokenService;
    this.tsdRabbitTemplate = tsdRabbitTemplate;
  }

  public void exportRequest(ExportRequest exportRequest)
      throws GenericException, IllegalArgumentException {

    String subject = tokenService.getSubject(exportRequest.getAccessToken());
    List<Visa> controlledAccessGrantsVisas =
        tokenService.filterByVisaType(
            tokenService.fetchTheFullPassportUsingPassportScopedAccessTokenAndGetVisas(
                exportRequest.getAccessToken()),
            VisaType.ControlledAccessGrants);
    log.info(
        "Elixir user {} authenticated and provided following valid GA4GH Visas: {}",
        subject,
        controlledAccessGrantsVisas);

    Set<Visa> collect =
        controlledAccessGrantsVisas.stream()
            .filter(
                visa -> {
                  String escapedId = Pattern.quote(exportRequest.getId());
                  return visa.getValue().matches(".*" + escapedId + ".*");
                })
            .collect(Collectors.toSet());

    if (collect.isEmpty()) {
      log.info(
          "No visas found for user {}. Requested to export {} {}",
          subject,
          exportRequest.getId(),
          exportRequest.getType());
      throw new GenericException(HttpStatus.BAD_REQUEST, "No visas found");
    }

    collect.stream()
        .findFirst()
        .ifPresent(
            (visa -> {
              log.info(
                  "Found {} visa(s). Using the first visa to make the request.", collect.size());

              exportRequest.setJwtToken(visa.getRawToken());

              tsdRabbitTemplate.convertAndSend(
                  exchange,
                  routingKey,
                  exportRequest.getInfoRequiredForDOAExportRequestAsJson(),
                  m -> {
                    m.getMessageProperties()
                        .setContentType(ContentType.APPLICATION_JSON.getMimeType());
                    m.getMessageProperties().setCorrelationId(UUID.randomUUID().toString());
                    return m;
                  });
              log.info(
                  "Export request: {} | Exchange: {} | Routing-key: {}",
                  exportRequest,
                  exchange,
                  routingKey);
            }));
  }
}
