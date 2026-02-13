package no.elixir.fega.ltp.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents the type of identifier used in an export request.
 *
 * <p>Supports deserialization from both camelCase ({@code "fileId"}, {@code "datasetId"}) and upper
 * snake case ({@code "FILE_ID"}, {@code "DATASET_ID"}) formats.
 */
@Getter
@ToString
public enum ExportRequestType {

  /** Indicates the export request targets a specific file. */
  FILE_ID("fileId"),

  /** Indicates the export request targets an entire dataset. */
  DATASET_ID("datasetId");

  /** The camelCase JSON representation of this type. */
  @JsonValue private final String value;

  /**
   * @param value the camelCase string used for JSON serialization
   */
  ExportRequestType(String value) {
    this.value = value;
  }

  /**
   * Deserializes a string into an {@link ExportRequestType}.
   *
   * <p>Accepts both camelCase values ({@code "fileId"}, {@code "datasetId"}) and enum constant
   * names ({@code "FILE_ID"}, {@code "DATASET_ID"}).
   *
   * @param value the string to deserialize
   * @return the matching {@link ExportRequestType}
   * @throws IllegalArgumentException if the value does not match any known type
   */
  @JsonCreator
  public static ExportRequestType fromValue(String value) {
    for (ExportRequestType type : ExportRequestType.values()) {
      if (type.value.equals(value) || type.name().equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown ExportType: " + value);
  }
}
