package no.elixir.fega.ltp.services;

import no.elixir.clearinghouse.model.Visa;
import no.elixir.fega.ltp.dto.ExportRequestDto;
import no.elixir.fega.ltp.exceptions.GenericException;
import no.elixir.fega.ltp.models.DOAExportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportRequestService Tests")
class ExportRequestServiceTest {

    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_VISA_TOKEN = "test-visa-token";
    private static final String TEST_DATASET_ID = "EGAD00001000001";
    private static final String TEST_PUBLIC_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ...";
    private static final String TEST_SUBJECT = "test-user@elixir.org";
    @Mock
    private TokenService tokenService;
    @Mock
    private RabbitTemplate tsdRabbitTemplate;
    @InjectMocks
    private ExportRequestService exportRequestService;
    @Captor
    private ArgumentCaptor<DOAExportRequest> messageCaptor;
    @Captor
    private ArgumentCaptor<String> exchangeCaptor;
    @Captor
    private ArgumentCaptor<String> routingKeyCaptor;
    private ExportRequestDto exportRequestDto;
    private Visa mockVisa;

    @BeforeEach
    void setUp() {
        // Initialize test data
        exportRequestDto = new ExportRequestDto();
        exportRequestDto.setAccessToken(TEST_ACCESS_TOKEN);
        exportRequestDto.setId(TEST_DATASET_ID);
        exportRequestDto.setUserPublicKey(TEST_PUBLIC_KEY);
        exportRequestDto.setType(ExportRequestDto.ExportType.DATASET_ID);

        // Mock Visa
        mockVisa = mock(Visa.class);
        when(mockVisa.getValue()).thenReturn("https://dac.elixir.org/" + TEST_DATASET_ID);
        when(mockVisa.getRawToken()).thenReturn(TEST_VISA_TOKEN);

        // Set private fields using reflection or test properties
        // Note: In real scenario, use @TestPropertySource or ReflectionTestUtils
    }

    @Test
    @DisplayName("Should successfully process GDI export request with valid visa")
    void exportRequestGDI_Success() throws GenericException {
        // Arrange
        when(tokenService.getSubject(TEST_ACCESS_TOKEN)).thenReturn(TEST_SUBJECT);
        when(tokenService.getControlledAccessGrantsVisas(TEST_ACCESS_TOKEN))
                .thenReturn(Collections.singletonList(mockVisa));

        // Act
        exportRequestService.exportRequestGDI(exportRequestDto);

        // Assert
        verify(tokenService).getSubject(TEST_ACCESS_TOKEN);
        verify(tokenService).getControlledAccessGrantsVisas(TEST_ACCESS_TOKEN);
        verify(tsdRabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(DOAExportRequest.class),
                any(MessagePostProcessor.class)
        );
        assertEquals(TEST_VISA_TOKEN, exportRequestDto.getVisaToken());
    }

    @Test
    @DisplayName("Should throw GenericException when no visas found")
    void exportRequestGDI_NoVisas_ThrowsException() {
        // Arrange
        when(tokenService.getSubject(TEST_ACCESS_TOKEN)).thenReturn(TEST_SUBJECT);
        when(tokenService.getControlledAccessGrantsVisas(TEST_ACCESS_TOKEN))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        GenericException exception = assertThrows(GenericException.class, () ->
                exportRequestService.exportRequestGDI(exportRequestDto)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("No visas found"));
        verify(tsdRabbitTemplate, never()).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class)
        );
    }

    @Test
    @DisplayName("Should throw GenericException when visa doesn't match resource ID")
    void exportRequestGDI_VisaDoesNotMatch_ThrowsException() {
        // Arrange
        Visa nonMatchingVisa = mock(Visa.class);
        when(nonMatchingVisa.getValue()).thenReturn("https://dac.elixir.org/DIFFERENT_ID");
        when(nonMatchingVisa.getRawToken()).thenReturn("different-token");

        when(tokenService.getSubject(TEST_ACCESS_TOKEN)).thenReturn(TEST_SUBJECT);
        when(tokenService.getControlledAccessGrantsVisas(TEST_ACCESS_TOKEN))
                .thenReturn(Collections.singletonList(nonMatchingVisa));

        // Act & Assert
        GenericException exception = assertThrows(GenericException.class, () ->
                exportRequestService.exportRequestGDI(exportRequestDto)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        verify(tsdRabbitTemplate, never()).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class)
        );
    }

    @Test
    @DisplayName("Should use first visa when multiple matching visas exist")
    void exportRequestGDI_MultipleVisas_UsesFirst() throws GenericException {
        // Arrange
        Visa secondVisa = mock(Visa.class);
        when(secondVisa.getValue()).thenReturn("https://dac.elixir.org/" + TEST_DATASET_ID);
        when(secondVisa.getRawToken()).thenReturn("second-visa-token");

        when(tokenService.getSubject(TEST_ACCESS_TOKEN)).thenReturn(TEST_SUBJECT);
        when(tokenService.getControlledAccessGrantsVisas(TEST_ACCESS_TOKEN))
                .thenReturn(Arrays.asList(mockVisa, secondVisa));

        // Act
        exportRequestService.exportRequestGDI(exportRequestDto);

        // Assert
        assertEquals(TEST_VISA_TOKEN, exportRequestDto.getVisaToken());
        verify(tsdRabbitTemplate).convertAndSend(
                anyString(), anyString(), any(DOAExportRequest.class), any(MessagePostProcessor.class)
        );
    }

    @Test
    @DisplayName("Should handle FILE_ID export type correctly")
    void exportRequestGDI_FileIdType_Success() throws GenericException {
        // Arrange
        String fileId = "EGAF00001000001";
        exportRequestDto.setId(fileId);
        exportRequestDto.setType(ExportRequestDto.ExportType.FILE_ID);

        Visa fileVisa = mock(Visa.class);
        when(fileVisa.getValue()).thenReturn("https://dac.elixir.org/" + fileId);
        when(fileVisa.getRawToken()).thenReturn(TEST_VISA_TOKEN);

        when(tokenService.getSubject(TEST_ACCESS_TOKEN)).thenReturn(TEST_SUBJECT);
        when(tokenService.getControlledAccessGrantsVisas(TEST_ACCESS_TOKEN))
                .thenReturn(Collections.singletonList(fileVisa));

        // Act
        exportRequestService.exportRequestGDI(exportRequestDto);

        // Assert
        verify(tsdRabbitTemplate).convertAndSend(
                anyString(), anyString(), messageCaptor.capture(), any(MessagePostProcessor.class)
        );
        DOAExportRequest capturedMessage = messageCaptor.getValue();
        assertEquals(fileId, capturedMessage.getFileId());
        assertNull(capturedMessage.getDatasetId());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null export request")
    void exportRequestGDI_NullRequest_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                exportRequestService.exportRequestGDI(null)
        );
        verify(tokenService, never()).getSubject(anyString());
    }

    @Test
    @DisplayName("Should successfully process FEGA export request")
    void exportRequestFEGA_Success() {
        // Arrange
        exportRequestDto.setVisaToken(TEST_VISA_TOKEN);

        // Act
        assertDoesNotThrow(() -> exportRequestService.exportRequestFEGA(exportRequestDto));

        // Assert
        verify(tsdRabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(DOAExportRequest.class),
                any(MessagePostProcessor.class)
        );
    }

    @Test
    @DisplayName("Should throw AmqpException when RabbitMQ is unavailable")
    void exportRequestFEGA_RabbitMQFailure_ThrowsAmqpException() {
        // Arrange
        exportRequestDto.setVisaToken(TEST_VISA_TOKEN);
        doThrow(new AmqpException("Connection failed"))
                .when(tsdRabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        // Act & Assert
        assertThrows(AmqpException.class, () ->
                exportRequestService.exportRequestFEGA(exportRequestDto)
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null FEGA export request")
    void exportRequestFEGA_NullRequest_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                exportRequestService.exportRequestFEGA(null)
        );
        verify(tsdRabbitTemplate, never()).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class)
        );
    }

    @Test
    @DisplayName("Should handle special characters in resource ID correctly")
    void exportRequestGDI_SpecialCharactersInId_Success() throws GenericException {
        // Arrange
        String specialId = "EGAD00001000001.v1+test";
        exportRequestDto.setId(specialId);

        Visa specialVisa = mock(Visa.class);
        when(specialVisa.getValue()).thenReturn("https://dac.elixir.org/" + specialId);
        when(specialVisa.getRawToken()).thenReturn(TEST_VISA_TOKEN);

        when(tokenService.getSubject(TEST_ACCESS_TOKEN)).thenReturn(TEST_SUBJECT);
        when(tokenService.getControlledAccessGrantsVisas(TEST_ACCESS_TOKEN))
                .thenReturn(Collections.singletonList(specialVisa));

        // Act
        exportRequestService.exportRequestGDI(exportRequestDto);

        // Assert
        verify(tsdRabbitTemplate).convertAndSend(
                anyString(), anyString(), any(DOAExportRequest.class), any(MessagePostProcessor.class)
        );
    }
}