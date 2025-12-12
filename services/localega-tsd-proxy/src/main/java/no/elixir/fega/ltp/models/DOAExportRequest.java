package no.elixir.fega.ltp.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import no.elixir.fega.ltp.dto.ExportRequestType;
import no.elixir.fega.ltp.dto.FegaExportRequestDto;
import no.elixir.fega.ltp.dto.GdiExportRequestDto;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DOAExportRequest {

  @JsonProperty("jwtToken")
  private String jwtToken;

  @JsonProperty("datasetId")
  private String datasetId;

  @JsonProperty("fileId")
  private String fileId;

  @JsonProperty("publicKey")
  private String publicKey;

  // Static factory method to create DOAExportRequest from ExportRequestDto
  public static DOAExportRequest fromExportRequestDto(
      GdiExportRequestDto gdiExportRequestDto, String jwtToken) {
    return createDOAExportRequest(
        jwtToken,
        gdiExportRequestDto.getUserPublicKey(),
        gdiExportRequestDto.getId(),
        gdiExportRequestDto.getType() == ExportRequestType.DATASET_ID);
  }

  public static DOAExportRequest fromExportRequestDto(FegaExportRequestDto exportRequestDto) {
    return createDOAExportRequest(
        exportRequestDto.getVisaToken(),
        exportRequestDto.getUserPublicKey(),
        exportRequestDto.getId(),
        exportRequestDto.getType() == ExportRequestType.DATASET_ID);
  }

  // Private helper method containing the shared logic
  private static DOAExportRequest createDOAExportRequest(
      String jwtToken, String publicKey, String id, boolean isDatasetId) {
    DOAExportRequest doaRequest = new DOAExportRequest();
    doaRequest.setJwtToken(jwtToken);
    doaRequest.setPublicKey(publicKey);
    if (isDatasetId) {
      doaRequest.setDatasetId(id);
      doaRequest.setFileId(null);
    } else {
      doaRequest.setFileId(id);
      doaRequest.setDatasetId(null);
    }
    return doaRequest;
  }
}
