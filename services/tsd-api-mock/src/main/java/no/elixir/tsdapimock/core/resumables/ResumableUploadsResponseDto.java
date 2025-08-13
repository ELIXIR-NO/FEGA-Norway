package no.elixir.tsdapimock.core.resumables;

import java.util.ArrayList;
import lombok.Data;

@Data
public class ResumableUploadsResponseDto {
  private ArrayList<ResumableUploadDto> resumables;

  public ResumableUploadsResponseDto(ArrayList<ResumableUploadDto> resumableDtos) {
    this.resumables = new ArrayList<>(resumableDtos);
  }
}
