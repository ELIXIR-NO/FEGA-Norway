package no.elixir.e2eTests.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Collection;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.CommonUtils;
import no.elixir.e2eTests.utils.TokenUtils;

public class InboxCleanupTest {

  /**
   * After mapping a file to a dataset, the SDA mapper removes the uploaded file from the inbox.
   * Nothing in the DB records that removal, so without this check a mapper that silently fails to
   * delete (wrong path, permission error) would still let the suite pass. We observe the inbox over
   * the network via the proxy's {@code GET /files?inbox=true} listing (the same endpoint the
   * outbox/export flow uses, flipped to the inbox), so the assertion holds whether the suite runs
   * in-container or as a jar against a host stack. Poll until the file is gone and fail if it never
   * is.
   *
   * <p>Inbox listing requires both the LS-AAI bearer (Proxy-Authorization) and CEGA basic auth, the
   * same pair the upload sends; only the outbox listing is exempt from CEGA auth. The bearer is
   * resolved exactly as the upload does ({@link no.elixir.e2eTests.features.UploadViaLegaCMDTest}):
   * a real LS-AAI token in EGA_DEV runs, else a minted visa token. Both carry the same subject, so
   * they map to the same inbox dir.
   */
  public static void verifyInboxFileRemovedAfterMapping() throws Exception {
    String fileName = E2EState.encFile.getName();
    String token = resolveListingToken();
    String listUrl =
        "https://%s:%s/files?inbox=true"
            .formatted(E2EState.env.getProxyHost(), E2EState.env.getProxyPort());
    E2EState.log.info("Verifying mapper removed '{}' from the inbox via {}", fileName, listUrl);

    int attempts = E2EState.env.getExportRequestMaxRetries();
    int intervalMillis = (int) (E2EState.env.getExportRequestIntervalInSeconds() * 1000);
    boolean present = true;
    for (int i = 1; i <= attempts; i++) {
      HttpResponse<FileListingResponse> res =
          Unirest.get(listUrl)
              .header("Proxy-Authorization", "Bearer " + token)
              .basicAuth(E2EState.env.getCegaAuthUsername(), E2EState.env.getCegaAuthPassword())
              .asObject(FileListingResponse.class);
      assertEquals(200, res.getStatus(), "inbox listing request failed: " + res.getStatus());

      present = res.getBody().files().stream().anyMatch(f -> fileName.equals(f.fileName()));
      if (!present) {
        E2EState.log.info("Inbox cleanup verified: '{}' gone after {} attempt(s)", fileName, i);
        break;
      }
      E2EState.log.info("'{}' still present in inbox, attempt {}/{}", fileName, i, attempts);
      CommonUtils.waitForProcessing(intervalMillis);
    }

    assertFalse(present, "Mapper did not remove the uploaded file from the inbox: " + fileName);
  }

  private static String resolveListingToken() throws Exception {
    String provided = E2EState.env.getLSAAIToken();
    if (provided == null || provided.isEmpty()) {
      return TokenUtils.generateVisaToken("upload", "jwt.pub.pem", "jwt.priv.pem");
    }
    return provided;
  }

  record FileListingResponse(Collection<TsdFile> files, String page) {}

  record TsdFile(
      String fileName,
      Long size,
      String modifiedDate,
      String href,
      Boolean exportable,
      String reason,
      String mimeType,
      String owner) {}
}
