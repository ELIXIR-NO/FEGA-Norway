package no.elixir.e2eTests.features;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Optional;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.CertificateUtils;
import no.elixir.e2eTests.utils.TokenUtils;

public class DownloadWithExportRequestTest {

  public static void testDownloadDatasetUsingExportRequestAndVerifyResults() throws Exception {
    String passportScopedAccessToken = E2EState.env.getLSAAIToken();
    E2EState.log.info(E2EState.encFile.getName());

    HttpResponse<JsonNode> exportRequestRes =
        callGdiExportRequestEndpoint(passportScopedAccessToken);
    assertEquals(200, exportRequestRes.getStatus());

    E2EState.log.info("Export request response: {}", exportRequestRes.getBody());
    HttpResponse<FileListingResponse> listFilesRes = checkFilesWithRetry(passportScopedAccessToken);
    assertNotNull(listFilesRes);
    assertEquals(200, listFilesRes.getStatus());

    E2EState.log.info("List user outbox request response: {}", listFilesRes.getBody());
    assertFalse(listFilesRes.getBody().files.isEmpty());

    Optional<TsdFile> first = listFilesRes.getBody().files.stream().findFirst();
    assertTrue(first.isPresent());
    assertEquals(E2EState.encFile.getName(), first.get().fileName);
  }

  public static void testDownloadDatasetUsingFegaExportRequestAndVerifyResults() throws Exception {
    String visaToken =
        TokenUtils.generateVisaToken(
            E2EState.datasetId,
            E2EState.env.getEgaDevJwtPubKeyPath(),
            E2EState.env.getEgaDevJwtPrivKeyPath());
    E2EState.log.info(E2EState.encFile.getName());
    String passportScopedAccessToken = E2EState.env.getLSAAIToken();

    HttpResponse<JsonNode> exportRequestRes = callFegaExportRequestEndpoint(visaToken);
    assertEquals(200, exportRequestRes.getStatus());

    E2EState.log.info("Export request response: {}", exportRequestRes.getBody());
    HttpResponse<FileListingResponse> listFilesRes = checkFilesWithRetry(passportScopedAccessToken);
    assertNotNull(listFilesRes);
    assertEquals(200, listFilesRes.getStatus());

    E2EState.log.info("List user outbox request response: {}", listFilesRes.getBody());
    assertFalse(listFilesRes.getBody().files.isEmpty());

    Optional<TsdFile> first = listFilesRes.getBody().files.stream().findFirst();
    assertTrue(first.isPresent());
    assertEquals(E2EState.encFile.getName(), first.get().fileName);
  }

  private static HttpResponse<JsonNode> callFegaExportRequestEndpoint(String visaToken)
      throws Exception {
    E2EState.log.info("Preparing to make fega export request");
    String exportReqUrl = buildFegaExportUrl();
    String payload = buildFegaExportPayload(visaToken);
    E2EState.log.info("Export request payload: {}", payload);
    return Unirest.post(exportReqUrl)
        .body(payload)
        .contentType("application/json")
        .basicAuth(E2EState.env.getProxyAdminUsername(), E2EState.env.getProxyAdminPassword())
        .asJson();
  }

  private static HttpResponse<JsonNode> callGdiExportRequestEndpoint(String accessToken)
      throws Exception {
    E2EState.log.info("Preparing to make export request");
    String exportReqUrl = buildGdiExportUrl();
    String payload = buildGdiExportPayload(accessToken);
    E2EState.log.info("Export request payload: {}", payload);
    return Unirest.post(exportReqUrl)
        .body(payload)
        .contentType("application/json")
        .basicAuth(E2EState.env.getProxyAdminUsername(), E2EState.env.getProxyAdminPassword())
        .asJson();
  }

  private static HttpResponse<FileListingResponse> checkFilesWithRetry(String accessToken)
      throws Exception {
    E2EState.log.info("Preparing to list files with retry (NOT DOWNLOADING ANYTHING)");
    String listFilesEndpoint = buildListFilesUrl();
    final int MAX_RETRIES = E2EState.env.getExportRequestMaxRetries();
    final long INTERVAL = E2EState.env.getExportRequestIntervalInSeconds();
    E2EState.log.info("Waiting {} second(s) before the initial call...", INTERVAL);
    Thread.sleep(1000 * INTERVAL);
    for (int i = 1; i <= MAX_RETRIES; i++) {
      HttpResponse<FileListingResponse> res =
          Unirest.get(listFilesEndpoint)
              .socketTimeout(300000)
              .header("Proxy-Authorization", "Bearer " + accessToken)
              .asObject(FileListingResponse.class);
      E2EState.log.info("List files request response: {}", res);
      // if something went wrong with this request
      if (200 != res.getStatus()) {
        return res;
      }
      FileListingResponse body = res.getBody();
      // Check if files array exists and is not empty
      if (!body.files.isEmpty()) {
        E2EState.log.info("Files found on attempt {}", i);
        return res;
      }
      if (i < MAX_RETRIES) {
        E2EState.log.info(
            "Files not found, waiting {} second(s) before attempt {}", INTERVAL, i + 1);
        Thread.sleep(1000 * INTERVAL);
      }
    }
    E2EState.log.warn("No files found after {} attempts", MAX_RETRIES);
    return null;
  }

  private static String buildGdiExportUrl() {
    return String.format(
        "https://%s:%s/export/gdi", E2EState.env.getProxyHost(), E2EState.env.getProxyPort());
  }

  private static String buildFegaExportUrl() {
    return String.format(
        "https://%s:%s/export/fega", E2EState.env.getProxyHost(), E2EState.env.getProxyPort());
  }

  private static String buildListFilesUrl() {
    return String.format(
        "https://%s:%s/files?inbox=false",
        E2EState.env.getProxyHost(), E2EState.env.getProxyPort());
  }

  private static String buildGdiExportPayload(String accessToken) throws Exception {
    File pubKey = CertificateUtils.getFile(E2EState.env.getEgaDevPubKeyPath());
    String jwtPublicKey =
        org.apache.commons.io.FileUtils.readFileToString(pubKey, Charset.defaultCharset());
    return Strings.EXPORT_REQ_BODY_GDI.formatted(
        E2EState.datasetId,
        accessToken,
        jwtPublicKey
            .replace(Strings.BEGIN_PUBLIC_KEY, "")
            .replace(Strings.END_PUBLIC_KEY, "")
            .replace(System.lineSeparator(), "")
            .replace(" ", "")
            .trim(),
        "DATASET_ID");
  }

  private static String buildFegaExportPayload(String visaToken) throws Exception {
    File pubKey = CertificateUtils.getFile(E2EState.env.getEgaDevPubKeyPath());
    String jwtPublicKey =
        org.apache.commons.io.FileUtils.readFileToString(pubKey, Charset.defaultCharset());
    return Strings.EXPORT_REQ_BODY_FEGA.formatted(
        E2EState.datasetId,
        visaToken,
        jwtPublicKey
            .replace(Strings.BEGIN_PUBLIC_KEY, "")
            .replace(Strings.END_PUBLIC_KEY, "")
            .replace(System.lineSeparator(), "")
            .replace(" ", "")
            .trim(),
        "DATASET_ID");
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
