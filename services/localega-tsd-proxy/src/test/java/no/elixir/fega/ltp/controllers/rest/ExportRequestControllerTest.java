package no.elixir.fega.ltp.controllers.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.elixir.fega.ltp.dto.ExportRequestDto;
import no.elixir.fega.ltp.exceptions.GenericException;
import no.elixir.fega.ltp.services.ExportRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportRequestController.class)
@DisplayName("ExportRequestController Integration Tests")
class ExportRequestControllerTest {

    private static final String EXPORT_ENDPOINT = "/export";
    private static final String EXPORT_FEGA_ENDPOINT = "/export/fega";
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "securePassword123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private ExportRequestService exportRequestService;
    private ExportRequestDto validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ExportRequestDto();
        validRequest.setAccessToken("valid-access-token");
        validRequest.setId("EGAD00001000001");
        validRequest.setUserPublicKey("ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ...");
        validRequest.setType(ExportRequestDto.ExportType.DATASET_ID);
    }

    // ==================== Authentication & Authorization Tests ====================

    @Test
    @DisplayName("Should return 401 when no authentication provided")
    void exportRequest_NoAuth_Returns401() throws Exception {
        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        verify(exportRequestService, never()).exportRequestGDI(any());
    }

    @Test
    @DisplayName("Should return 401 when invalid credentials provided")
    void exportRequest_InvalidCredentials_Returns401() throws Exception {
        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .with(httpBasic("wronguser", "wrongpassword"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        verify(exportRequestService, never()).exportRequestGDI(any());
    }

    @Test
    @DisplayName("Should return 200 when valid credentials provided")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequest_ValidAuth_Returns200() throws Exception {
        doNothing().when(exportRequestService).exportRequestGDI(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Export request completed successfully"));

        verify(exportRequestService, times(1)).exportRequestGDI(any(ExportRequestDto.class));
    }

    @Test
    @DisplayName("Should return 403 when user doesn't have ADMIN role")
    @WithMockUser(username = "user", roles = {"USER"})
    void exportRequest_NonAdminUser_Returns403() throws Exception {
        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isForbidden());

        verify(exportRequestService, never()).exportRequestGDI(any());
    }

    // ==================== GDI Export Request Tests ====================

    @Test
    @DisplayName("Should successfully process valid GDI export request")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestGDI_ValidRequest_Success() throws Exception {
        doNothing().when(exportRequestService).exportRequestGDI(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Export request completed successfully"));

        verify(exportRequestService, times(1)).exportRequestGDI(any(ExportRequestDto.class));
    }

    @Test
    @DisplayName("Should return 400 when required field is missing")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestGDI_MissingField_Returns400() throws Exception {
        validRequest.setAccessToken(null); // Missing required field

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(exportRequestService, never()).exportRequestGDI(any());
    }

    @Test
    @DisplayName("Should return 400 when field is blank")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestGDI_BlankField_Returns400() throws Exception {
        validRequest.setAccessToken("   "); // Blank field

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(exportRequestService, never()).exportRequestGDI(any());
    }

    @Test
    @DisplayName("Should return 400 when GenericException thrown with BAD_REQUEST status")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestGDI_GenericException_Returns400() throws Exception {
        String errorMessage = "No visas found";
        doThrow(new GenericException(HttpStatus.BAD_REQUEST, errorMessage))
                .when(exportRequestService).exportRequestGDI(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(errorMessage));
    }

    @Test
    @DisplayName("Should return 403 when GenericException thrown with FORBIDDEN status")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestGDI_ForbiddenException_Returns403() throws Exception {
        String errorMessage = "Access denied";
        doThrow(new GenericException(HttpStatus.FORBIDDEN, errorMessage))
                .when(exportRequestService).exportRequestGDI(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(errorMessage));
    }

    @Test
    @DisplayName("Should return 400 when IllegalArgumentException thrown")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestGDI_IllegalArgumentException_Returns400() throws Exception {
        String errorMessage = "Invalid argument";
        doThrow(new IllegalArgumentException(errorMessage))
                .when(exportRequestService).exportRequestGDI(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(errorMessage));
    }

    @Test
    @DisplayName("Should handle FILE_ID export type")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestGDI_FileIdType_Success() throws Exception {
        validRequest.setId("EGAF00001000001");
        validRequest.setType(ExportRequestDto.ExportType.FILE_ID);
        doNothing().when(exportRequestService).exportRequestGDI(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Export request completed successfully"));

        verify(exportRequestService, times(1)).exportRequestGDI(any(ExportRequestDto.class));
    }

    // ==================== FEGA Export Request Tests ====================

    @Test
    @DisplayName("Should successfully process valid FEGA export request")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestFEGA_ValidRequest_Success() throws Exception {
        doNothing().when(exportRequestService).exportRequestFEGA(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_FEGA_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Export request completed successfully"));

        verify(exportRequestService, times(1)).exportRequestFEGA(any(ExportRequestDto.class));
    }

    @Test
    @DisplayName("Should return 401 when FEGA request without authentication")
    void exportRequestFEGA_NoAuth_Returns401() throws Exception {
        mockMvc.perform(post(EXPORT_FEGA_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        verify(exportRequestService, never()).exportRequestFEGA(any());
    }

    @Test
    @DisplayName("Should return 503 when RabbitMQ is unavailable")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestFEGA_RabbitMQDown_Returns503() throws Exception {
        doThrow(new AmqpException("Connection failed"))
                .when(exportRequestService).exportRequestFEGA(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_FEGA_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("Message queue")));
    }

    @Test
    @DisplayName("Should return 400 when FEGA request has invalid data")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequestFEGA_InvalidRequest_Returns400() throws Exception {
        validRequest.setId(""); // Invalid empty ID

        mockMvc.perform(post(EXPORT_FEGA_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(exportRequestService, never()).exportRequestFEGA(any());
    }

    @Test
    @DisplayName("Should return 400 when request body is null")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequest_NullBody_Returns400() throws Exception {
        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(exportRequestService, never()).exportRequestGDI(any());
    }

    @Test
    @DisplayName("Should return 400 when request content type is not JSON")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequest_WrongContentType_Returns400() throws Exception {
        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("invalid content"))
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType());

        verify(exportRequestService, never()).exportRequestGDI(any());
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle very long IDs")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequest_VeryLongId_Success() throws Exception {
        String longId = "A".repeat(1000);
        validRequest.setId(longId);
        doNothing().when(exportRequestService).exportRequestGDI(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(exportRequestService, times(1)).exportRequestGDI(any(ExportRequestDto.class));
    }

    @Test
    @DisplayName("Should handle special characters in ID")
    @WithMockUser(username = TEST_USERNAME, roles = {"ADMIN"})
    void exportRequest_SpecialCharactersInId_Success() throws Exception {
        validRequest.setId("EGAD00001000001.v1+test-2024");
        doNothing().when(exportRequestService).exportRequestGDI(any(ExportRequestDto.class));

        mockMvc.perform(post(EXPORT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(exportRequestService, times(1)).exportRequestGDI(any(ExportRequestDto.class));
    }
}