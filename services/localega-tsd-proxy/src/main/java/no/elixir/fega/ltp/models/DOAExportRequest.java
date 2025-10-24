package no.elixir.fega.ltp.models;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import no.elixir.fega.ltp.dto.ExportRequestDto;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DOAExportRequest {

    @SerializedName("jwtToken")
    private String jwtToken; // this is the Visa token it expects

    @SerializedName("datasetId")
    private String datasetId;

    @SerializedName("fileId")
    private String fileId;

    @SerializedName("publicKey")
    private String publicKey;

    // Static factory method to create DOAExportRequest from ExportRequestDto
    public static DOAExportRequest fromExportRequestDto(ExportRequestDto exportRequestDto) {
        DOAExportRequest doaRequest = new DOAExportRequest();
        // Always map jwtToken and publicKey
        doaRequest.setJwtToken(exportRequestDto.getVisaToken());
        doaRequest.setPublicKey(exportRequestDto.getUserPublicKey());
        // Map id field based on type, explicitly set the other to null
        if (exportRequestDto.getType() == ExportRequestDto.ExportType.DATASET_ID) {
            doaRequest.setDatasetId(exportRequestDto.getId());
            doaRequest.setFileId(null); // Explicitly set to null
        } else if (exportRequestDto.getType() == ExportRequestDto.ExportType.FILE_ID) {
            doaRequest.setFileId(exportRequestDto.getId());
            doaRequest.setDatasetId(null); // Explicitly set to null
        }
        return doaRequest;
    }
}
