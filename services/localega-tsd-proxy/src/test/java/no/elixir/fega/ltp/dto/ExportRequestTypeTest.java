package no.elixir.fega.ltp.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExportRequestTypeTest {

  @Test
  void fromValueWithCamelCaseFileId() {
    assertEquals(ExportRequestType.FILE_ID, ExportRequestType.fromValue("fileId"));
  }

  @Test
  void fromValueWithCamelCaseDatasetId() {
    assertEquals(ExportRequestType.DATASET_ID, ExportRequestType.fromValue("datasetId"));
  }

  @Test
  void fromValueWithUpperSnakeCaseFileId() {
    assertEquals(ExportRequestType.FILE_ID, ExportRequestType.fromValue("FILE_ID"));
  }

  @Test
  void fromValueWithUpperSnakeCaseDatasetId() {
    assertEquals(ExportRequestType.DATASET_ID, ExportRequestType.fromValue("DATASET_ID"));
  }

  @Test
  void fromValueWithUnknownValueThrowsException() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> ExportRequestType.fromValue("unknown"));
    assertEquals("Unknown ExportType: unknown", ex.getMessage());
  }

  @Test
  void jsonValueReturnsCamelCase() {
    assertEquals("fileId", ExportRequestType.FILE_ID.getValue());
    assertEquals("datasetId", ExportRequestType.DATASET_ID.getValue());
  }
}
