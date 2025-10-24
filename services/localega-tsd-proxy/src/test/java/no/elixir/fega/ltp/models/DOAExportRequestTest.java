package no.elixir.fega.ltp.models;

import no.elixir.fega.ltp.dto.ExportRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DOAExportRequest Model Tests")
class DOAExportRequestTest {

    private static final String TEST_JWT_TOKEN = "test-jwt-token";
    private static final String TEST_DATASET_ID = "EGAD00001000001";
    private static final String TEST_FILE_ID = "EGAF00001000001";
    private static final String TEST_PUBLIC_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ...";

    @Test
    @DisplayName("Should create DOAExportRequest from ExportRequestDto with DATASET_ID type")
    void fromExportRequestDto_DatasetIdType_Success() {
        // Arrange
        ExportRequestDto dto = new ExportRequestDto();
        dto.setVisaToken(TEST_JWT_TOKEN);
        dto.setId(TEST_DATASET_ID);
        dto.setUserPublicKey(TEST_PUBLIC_KEY);
        dto.setType(ExportRequestDto.ExportType.DATASET_ID);

        // Act
        DOAExportRequest result = DOAExportRequest.fromExportRequestDto(dto);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_JWT_TOKEN, result.getJwtToken());
        assertEquals(TEST_DATASET_ID, result.getDatasetId());
        assertNull(result.getFileId());
        assertEquals(TEST_PUBLIC_KEY, result.getPublicKey());
    }

    @Test
    @DisplayName("Should create DOAExportRequest from ExportRequestDto with FILE_ID type")
    void fromExportRequestDto_FileIdType_Success() {
        // Arrange
        ExportRequestDto dto = new ExportRequestDto();
        dto.setVisaToken(TEST_JWT_TOKEN);
        dto.setId(TEST_FILE_ID);
        dto.setUserPublicKey(TEST_PUBLIC_KEY);
        dto.setType(ExportRequestDto.ExportType.FILE_ID);

        // Act
        DOAExportRequest result = DOAExportRequest.fromExportRequestDto(dto);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_JWT_TOKEN, result.getJwtToken());
        assertNull(result.getDatasetId());
        assertEquals(TEST_FILE_ID, result.getFileId());
        assertEquals(TEST_PUBLIC_KEY, result.getPublicKey());
    }

    @Test
    @DisplayName("Should explicitly set fileId to null when type is DATASET_ID")
    void fromExportRequestDto_DatasetIdType_FileIdIsNull() {
        // Arrange
        ExportRequestDto dto = new ExportRequestDto();
        dto.setVisaToken(TEST_JWT_TOKEN);
        dto.setId(TEST_DATASET_ID);
        dto.setUserPublicKey(TEST_PUBLIC_KEY);
        dto.setType(ExportRequestDto.ExportType.DATASET_ID);

        // Act
        DOAExportRequest result = DOAExportRequest.fromExportRequestDto(dto);

        // Assert
        assertNull(result.getFileId(), "FileId should be explicitly null for DATASET_ID type");
        assertNotNull(result.getDatasetId());
    }

    @Test
    @DisplayName("Should explicitly set datasetId to null when type is FILE_ID")
    void fromExportRequestDto_FileIdType_DatasetIdIsNull() {
        // Arrange
        ExportRequestDto dto = new ExportRequestDto();
        dto.setVisaToken(TEST_JWT_TOKEN);
        dto.setId(TEST_FILE_ID);
        dto.setUserPublicKey(TEST_PUBLIC_KEY);
        dto.setType(ExportRequestDto.ExportType.FILE_ID);

        // Act
        DOAExportRequest result = DOAExportRequest.fromExportRequestDto(dto);

        // Assert
        assertNull(result.getDatasetId(), "DatasetId should be explicitly null for FILE_ID type");
        assertNotNull(result.getFileId());
    }

    @Test
    @DisplayName("Should handle null values in ExportRequestDto")
    void fromExportRequestDto_NullValues_Success() {
        // Arrange
        ExportRequestDto dto = new ExportRequestDto();
        dto.setVisaToken(null);
        dto.setId(TEST_DATASET_ID);
        dto.setUserPublicKey(null);
        dto.setType(ExportRequestDto.ExportType.DATASET_ID);

        // Act
        DOAExportRequest result = DOAExportRequest.fromExportRequestDto(dto);

        // Assert
        assertNotNull(result);
        assertNull(result.getJwtToken());
        assertNull(result.getPublicKey());
        assertEquals(TEST_DATASET_ID, result.getDatasetId());
    }

    @Test
    @DisplayName("Should test Lombok generated methods - AllArgsConstructor")
    void testAllArgsConstructor() {
        // Act
        DOAExportRequest request = new DOAExportRequest(
                TEST_JWT_TOKEN,
                TEST_DATASET_ID,
                null,
                TEST_PUBLIC_KEY
        );

        // Assert
        assertEquals(TEST_JWT_TOKEN, request.getJwtToken());
        assertEquals(TEST_DATASET_ID, request.getDatasetId());
        assertNull(request.getFileId());
        assertEquals(TEST_PUBLIC_KEY, request.getPublicKey());
    }

    @Test
    @DisplayName("Should test Lombok generated methods - NoArgsConstructor")
    void testNoArgsConstructor() {
        // Act
        DOAExportRequest request = new DOAExportRequest();

        // Assert
        assertNotNull(request);
        assertNull(request.getJwtToken());
        assertNull(request.getDatasetId());
        assertNull(request.getFileId());
        assertNull(request.getPublicKey());
    }

    @Test
    @DisplayName("Should test Lombok generated methods - Setters and Getters")
    void testSettersAndGetters() {
        // Arrange
        DOAExportRequest request = new DOAExportRequest();

        // Act
        request.setJwtToken(TEST_JWT_TOKEN);
        request.setDatasetId(TEST_DATASET_ID);
        request.setFileId(TEST_FILE_ID);
        request.setPublicKey(TEST_PUBLIC_KEY);

        // Assert
        assertEquals(TEST_JWT_TOKEN, request.getJwtToken());
        assertEquals(TEST_DATASET_ID, request.getDatasetId());
        assertEquals(TEST_FILE_ID, request.getFileId());
        assertEquals(TEST_PUBLIC_KEY, request.getPublicKey());
    }

    @Test
    @DisplayName("Should test Lombok generated equals and hashCode")
    void testEqualsAndHashCode() {
        // Arrange
        DOAExportRequest request1 = new DOAExportRequest(
                TEST_JWT_TOKEN,
                TEST_DATASET_ID,
                null,
                TEST_PUBLIC_KEY
        );

        DOAExportRequest request2 = new DOAExportRequest(
                TEST_JWT_TOKEN,
                TEST_DATASET_ID,
                null,
                TEST_PUBLIC_KEY
        );

        DOAExportRequest request3 = new DOAExportRequest(
                "different-token",
                TEST_DATASET_ID,
                null,
                TEST_PUBLIC_KEY
        );

        // Assert
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1.hashCode(), request3.hashCode());
    }

    @Test
    @DisplayName("Should test Lombok generated toString")
    void testToString() {
        // Arrange
        DOAExportRequest request = new DOAExportRequest(
                TEST_JWT_TOKEN,
                TEST_DATASET_ID,
                null,
                TEST_PUBLIC_KEY
        );

        // Act
        String result = request.toString();

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("DOAExportRequest"));
        assertTrue(result.contains(TEST_JWT_TOKEN));
        assertTrue(result.contains(TEST_DATASET_ID));
    }

    @Test
    @DisplayName("Should handle special characters in ID fields")
    void fromExportRequestDto_SpecialCharacters_Success() {
        // Arrange
        String specialId = "EGAD00001000001.v1+test-2024";
        ExportRequestDto dto = new ExportRequestDto();
        dto.setVisaToken(TEST_JWT_TOKEN);
        dto.setId(specialId);
        dto.setUserPublicKey(TEST_PUBLIC_KEY);
        dto.setType(ExportRequestDto.ExportType.DATASET_ID);

        // Act
        DOAExportRequest result = DOAExportRequest.fromExportRequestDto(dto);

        // Assert
        assertEquals(specialId, result.getDatasetId());
    }

    @Test
    @DisplayName("Should handle very long strings")
    void fromExportRequestDto_VeryLongStrings_Success() {
        // Arrange
        String longToken = "A".repeat(10000);
        String longId = "B".repeat(5000);
        ExportRequestDto dto = new ExportRequestDto();
        dto.setVisaToken(longToken);
        dto.setId(longId);
        dto.setUserPublicKey(TEST_PUBLIC_KEY);
        dto.setType(ExportRequestDto.ExportType.FILE_ID);

        // Act
        DOAExportRequest result = DOAExportRequest.fromExportRequestDto(dto);

        // Assert
        assertEquals(longToken, result.getJwtToken());
        assertEquals(longId, result.getFileId());
    }
}