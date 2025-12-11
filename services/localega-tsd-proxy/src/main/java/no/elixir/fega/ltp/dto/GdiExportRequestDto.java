package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import no.elixir.fega.ltp.models.DOAExportRequest;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class GdiExportRequestDto {

    @NotBlank(message = "The field 'accessToken' must not be blank.")
    @JsonProperty("accessToken")
    private String accessToken;

    @NotBlank(message = "The field 'id' must not be blank.")
    @JsonProperty("id")
    private String id;

    @NotBlank(message = "The field 'userPublicKey' must not be blank.")
    @JsonProperty("userPublicKey")
    private String userPublicKey;

    @NotNull(message = "The field 'type' must not be null. Should be either 'fileId' or 'datasetId'.")
    @JsonProperty("type")
    private ExportRequestType type = ExportRequestType.DATASET_ID;

}
