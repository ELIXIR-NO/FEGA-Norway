package no.elixir.fega.ltp.errors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * The stable codes the proxy answers failures with, see ELIXIR-NO/FEGA-Norway#913. Clients switch
 * on the name, which goes out as the {@code code} member of the problem, so a name never changes
 * once released. The detail is fixed per code: it never carries exception or upstream text.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  MISSING_CREDENTIALS(
      HttpStatus.UNAUTHORIZED,
      "Missing credentials",
      "The request lacks the credentials this endpoint requires."),
  ELIXIR_AAI_TOKEN_INVALID(
      HttpStatus.UNAUTHORIZED,
      "Invalid LS AAI token",
      "The LS AAI token is invalid, expired or not issued for this service."),
  EGA_AUTH_FAILED(
      HttpStatus.UNAUTHORIZED,
      "EGA authentication failed",
      "The EGA username or password is wrong."),
  ADMIN_AUTH_FAILED(
      HttpStatus.UNAUTHORIZED,
      "Admin authentication failed",
      "The admin username or password is wrong."),
  VISA_DENIED(
      HttpStatus.FORBIDDEN,
      "Access denied",
      "No valid visa grants access to the requested resource."),
  ACCOUNT_MAPPING_CONFLICT(
      HttpStatus.FORBIDDEN,
      "Account mapping conflict",
      "This EGA account is already linked to a different LS AAI account."),
  FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "File not found", "The requested file does not exist."),
  FOLDER_NOT_FOUND(
      HttpStatus.NOT_FOUND,
      "Folder not found",
      "The folder does not exist yet; the first upload creates it."),
  INVALID_REQUEST(
      HttpStatus.BAD_REQUEST,
      "Invalid request",
      "The request is malformed or misses a required parameter."),
  CHECKSUM_MISMATCH(
      HttpStatus.UNPROCESSABLE_CONTENT,
      "Checksum mismatch",
      "The uploaded data does not match its MD5 checksum, so the upload cannot be resumed; upload"
          + " the whole file again."),
  CEGA_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE,
      "CEGA unavailable",
      "Central EGA could not be reached to check the EGA credentials; try again later."),
  UPSTREAM_FAILED(
      HttpStatus.BAD_GATEWAY,
      "Upstream service failed",
      "A service the proxy depends on failed; try again later."),
  INTERNAL(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Internal error",
      "The proxy failed to handle the request.");

  private final HttpStatus status;
  private final String title;
  private final String detail;
}
