package no.elixir.fega.ltp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import java.util.Collections;
import java.util.List;
import no.elixir.clearinghouse.model.Visa;
import no.elixir.fega.ltp.dto.ExportRequestType;
import no.elixir.fega.ltp.dto.GdiExportRequestDto;
import no.elixir.fega.ltp.errors.ErrorCode;
import no.elixir.fega.ltp.errors.ProxyException;
import no.elixir.fega.ltp.models.DOAExportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

class ExportRequestServiceTest {

  private TokenService tokenService;
  private RabbitTemplate tsdRabbitTemplate;
  private ExportRequestService service;

  @BeforeEach
  void setUp() {
    tokenService = mock(TokenService.class);
    tsdRabbitTemplate = mock(RabbitTemplate.class);
    service =
        new ExportRequestService(
            tokenService,
            tsdRabbitTemplate,
            mock(JsonSchemaValidationService.class),
            mock(ObjectMapper.class));
  }

  /**
   * Regression for #863, access-token half: a token that fails verification ends as
   * ELIXIR_AAI_TOKEN_INVALID (#913), never the advice's 500 fallback, and nothing reaches DOA.
   * TokenServiceTest covers how a {@link SignatureException} becomes that code.
   */
  @Test
  void exportRequestGDI_throwsTokenInvalid_whenAccessTokenSignatureFails() {
    when(tokenService.parseVerified(anyString()))
        .thenThrow(
            new ProxyException(
                ErrorCode.ELIXIR_AAI_TOKEN_INVALID,
                "verification failed",
                new SignatureException("JWT signature does not match")));

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOfSatisfying(
            ProxyException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ELIXIR_AAI_TOKEN_INVALID));
    verifyNoInteractions(tsdRabbitTemplate);
  }

  @Test
  void exportRequestGDI_throwsTokenInvalid_whenVisaSignatureFails() {
    Claims claims = claimsWithSubject();
    when(tokenService.parseVerified(anyString())).thenReturn(claims);
    when(tokenService.getControlledAccessGrantsVisas(anyString()))
        .thenThrow(
            new ProxyException(
                ErrorCode.ELIXIR_AAI_TOKEN_INVALID,
                "verification failed",
                new SignatureException("JWT signature does not match")));

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOfSatisfying(
            ProxyException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ELIXIR_AAI_TOKEN_INVALID));
    verifyNoInteractions(tsdRabbitTemplate);
  }

  @Test
  void exportRequestGDI_throwsVisaDenied_whenVisaListIsEmpty() {
    Claims claims = claimsWithSubject();
    when(tokenService.parseVerified(anyString())).thenReturn(claims);
    when(tokenService.getControlledAccessGrantsVisas(anyString()))
        .thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOfSatisfying(
            ProxyException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VISA_DENIED));
    verifyNoInteractions(tsdRabbitTemplate);
  }

  @Test
  void exportRequestGDI_throwsVisaDenied_whenNoVisaMatchesResource() {
    Claims claims = claimsWithSubject();
    when(tokenService.parseVerified(anyString())).thenReturn(claims);
    Visa otherDataset = visaFor("EGAD00000000002", "other-visa-token");
    when(tokenService.getControlledAccessGrantsVisas(anyString()))
        .thenReturn(List.of(otherDataset));

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOfSatisfying(
            ProxyException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VISA_DENIED));
    verifyNoInteractions(tsdRabbitTemplate);
  }

  @Test
  void exportRequestGDI_sendsMatchingVisaToDoa_whenVisaMatchesResource() {
    Claims claims = claimsWithSubject();
    when(tokenService.parseVerified(anyString())).thenReturn(claims);
    Visa matching = visaFor("EGAD00000000001", "matching-visa-token");
    when(tokenService.getControlledAccessGrantsVisas(anyString())).thenReturn(List.of(matching));

    service.exportRequestGDI(gdiBody());

    ArgumentCaptor<DOAExportRequest> sent = ArgumentCaptor.forClass(DOAExportRequest.class);
    verify(tsdRabbitTemplate)
        .convertAndSend(any(), any(), sent.capture(), any(MessagePostProcessor.class));
    assertThat(sent.getValue().getJwtToken()).isEqualTo("matching-visa-token");
    assertThat(sent.getValue().getDatasetId()).isEqualTo("EGAD00000000001");
  }

  @Test
  void exportRequestGDI_throwsTokenInvalid_whenSubjectIsBlank() {
    Claims claims = claimsWithSubject("");
    when(tokenService.parseVerified(anyString())).thenReturn(claims);

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOfSatisfying(
            ProxyException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ELIXIR_AAI_TOKEN_INVALID));
    verify(tokenService, never()).getControlledAccessGrantsVisas(anyString());
    verifyNoInteractions(tsdRabbitTemplate);
  }

  private static Claims claimsWithSubject() {
    return claimsWithSubject("dummy@elixir-europe.org");
  }

  private static Claims claimsWithSubject(String subject) {
    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn(subject);
    return claims;
  }

  private static Visa visaFor(String datasetId, String rawToken) {
    Visa visa = mock(Visa.class);
    when(visa.getValue()).thenReturn("https://ega-archive.org/datasets/" + datasetId);
    when(visa.getRawToken()).thenReturn(rawToken);
    return visa;
  }

  private static GdiExportRequestDto gdiBody() {
    return new GdiExportRequestDto(
        "forged-token", "EGAD00000000001", "user-public-key", ExportRequestType.DATASET_ID);
  }
}
