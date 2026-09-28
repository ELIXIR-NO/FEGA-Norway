package no.elixir.fega.ltp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import java.util.Collections;
import no.elixir.fega.ltp.dto.ExportRequestType;
import no.elixir.fega.ltp.dto.GdiExportRequestDto;
import no.elixir.fega.ltp.exceptions.GenericException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
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
   * Regression for #863, access-token half: {@code parseVerified} already throws {@link
   * SignatureException} on main. Guards the mapping of that throw to {@link GenericException}
   * FORBIDDEN; without it the controller catch-all answers 500.
   */
  @Test
  void exportRequestGDI_throwsForbidden_whenAccessTokenSignatureFails() {
    when(tokenService.parseVerified(anyString()))
        .thenThrow(new SignatureException("JWT signature does not match"));

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOfSatisfying(
            GenericException.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(e.getMessage()).isEqualTo("Token verification failed");
            });
    verifyNoInteractions(tsdRabbitTemplate);
  }

  @Test
  void exportRequestGDI_throwsForbidden_whenVisaSignatureFails() {
    Claims claims = claimsWithSubject();
    when(tokenService.parseVerified(anyString())).thenReturn(claims);
    when(tokenService.getControlledAccessGrantsVisas(anyString()))
        .thenThrow(new SignatureException("JWT signature does not match"));

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOfSatisfying(
            GenericException.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(e.getMessage()).isEqualTo("Token verification failed");
            });
    verifyNoInteractions(tsdRabbitTemplate);
  }

  @Test
  void exportRequestGDI_throwsForbidden_whenVisaListIsEmpty() {
    Claims claims = claimsWithSubject();
    when(tokenService.parseVerified(anyString())).thenReturn(claims);
    when(tokenService.getControlledAccessGrantsVisas(anyString()))
        .thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOfSatisfying(
            GenericException.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(e.getMessage()).isEqualTo("No valid visas found for this resource");
            });
    verifyNoInteractions(tsdRabbitTemplate);
  }

  @Test
  void exportRequestGDI_throwsIllegalArgument_whenSubjectIsBlank() {
    Claims claims = claimsWithSubject("");
    when(tokenService.parseVerified(anyString())).thenReturn(claims);

    assertThatThrownBy(() -> service.exportRequestGDI(gdiBody()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Access token missing subject");
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

  private static GdiExportRequestDto gdiBody() {
    return new GdiExportRequestDto(
        "forged-token", "EGAD00000000001", "user-public-key", ExportRequestType.DATASET_ID);
  }
}
