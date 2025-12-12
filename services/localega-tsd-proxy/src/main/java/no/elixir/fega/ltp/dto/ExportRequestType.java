package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public enum ExportRequestType {
  FILE_ID("fileId"),
  DATASET_ID("datasetId");
  @JsonValue private final String value;

  ExportRequestType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static ExportRequestType fromValue(@JsonProperty String value) {
    for (ExportRequestType type : ExportRequestType.values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown ExportType: " + value);
  }
}
