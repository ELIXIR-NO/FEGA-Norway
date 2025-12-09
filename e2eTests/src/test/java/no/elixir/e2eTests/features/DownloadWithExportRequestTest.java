package no.elixir.e2eTests.features;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import no.elixir.crypt4gh.stream.Crypt4GHInputStream;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.CertificateUtils;
import no.elixir.e2eTests.utils.TokenUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

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

    Stream<TsdFile> stream =
        listFilesRes.getBody().files.stream()
            .filter(file -> E2EState.encFile.getName().equals(file.fileName));
    List<TsdFile> files = stream.toList();
    assertEquals(1, files.size());
    assertEquals(E2EState.encFile.getName(), files.getFirst().fileName);

    String basedir = E2EState.env.getEgaDevBaseDirectory();
    if (!basedir.endsWith("/")) {
      basedir += "/";
    }
    Path filePath =
        downloadFileViaProxy(
            E2EState.encFile.getName(),
            passportScopedAccessToken,
            basedir + "out/" + E2EState.encFile.getName());
        assertAll("File validation",
            () -> assertTrue(Files.exists(filePath), "File should exist"),
            () -> assertTrue(Files.isRegularFile(filePath), "Should be a regular file"),
            () -> assertTrue(Files.size(filePath) > 0, "File should not be empty")
    );

    E2EState.log.info("Decrypting the downloaded file...");

    // Decrypt using recipient's private key
    ByteArrayOutputStream decryptedOutput = new ByteArrayOutputStream();
    try (InputStream encryptedInputStream = Files.newInputStream(filePath);
         Crypt4GHInputStream crypt4GHInputStream =
                 new Crypt4GHInputStream(encryptedInputStream, E2EState.recipientKeypair.getPrivate())) {
      IOUtils.copyLarge(crypt4GHInputStream, decryptedOutput);
    }

    byte[] decryptedData = decryptedOutput.toByteArray();

    // Calculate checksums of decrypted data
    String decryptedSHA256 = Hex.encodeHexString(DigestUtils.sha256(decryptedData));
    String decryptedMD5 = Hex.encodeHexString(DigestUtils.md5(decryptedData));

    E2EState.log.info("Decrypted SHA256 checksum: {}", decryptedSHA256);
    E2EState.log.info("Decrypted MD5 checksum: {}", decryptedMD5);

    // Verify checksums match the original file
    assertAll("Checksum validation",
            () -> assertEquals(E2EState.rawSHA256Checksum, decryptedSHA256,
                    "SHA256 checksum should match original file"),
            () -> assertEquals(E2EState.rawMD5Checksum, decryptedMD5,
                    "MD5 checksum should match original file")
    );

    E2EState.log.info("File successfully decrypted and checksums verified!");
  }

  private static Path downloadFileViaProxy(String fileName, String accessToken, String outputPath)
      throws IOException {
    E2EState.log.info("Downloading file via proxy... fileName: {}, outputPath: {}", fileName, outputPath);
    HttpResponse<byte[]> downloadRes =
        Unirest.get(buildProxyDownloadUrl(fileName))
            .header("Proxy-Authorization", "Bearer " + accessToken)
            .asBytes();
    assertEquals(200, downloadRes.getStatus(), "proxy was not happy when downloading...");
    byte[] bytes = downloadRes.getBody();
    return Files.write(Paths.get(outputPath), bytes);
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

  private static String buildProxyDownloadUrl(String fileName) {
    return String.format(
        "https://%s:%s/stream/%s",
        E2EState.env.getProxyHost(), E2EState.env.getProxyPort(), fileName);
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
    // Get the public key from the KeyPair
    PublicKey publicKey = E2EState.recipientKeypair.getPublic();
    // Convert to Base64-encoded string (DER format)
    String base64PublicKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());
    return Strings.EXPORT_REQ_BODY_FEGA.formatted(
            E2EState.datasetId,
            visaToken,
            base64PublicKey, "DATASET_ID");
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
