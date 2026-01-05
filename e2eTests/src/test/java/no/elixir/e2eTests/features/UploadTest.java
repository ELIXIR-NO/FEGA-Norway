package no.elixir.e2eTests.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.TokenUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

public class UploadTest {

  public static void uploadThroughProxy() throws Exception {
    E2EState.log.info("Uploading a file through a proxy...");
    String token = resolveUploadToken();
    E2EState.log.info("Visa JWT token when uploading: {}", token);
    String md5Hex = DigestUtils.md5Hex(Files.newInputStream(E2EState.encFile.toPath()));
    E2EState.log.info("Encrypted MD5 checksum: {}", md5Hex);
    E2EState.log.info("Cega Auth Username: {}", E2EState.env.getCegaAuthUsername());
    String uploadURL =
        String.format(
            "https://%s:%s/stream/%s?md5=%s",
            E2EState.env.getProxyHost(),
            E2EState.env.getProxyPort(),
            E2EState.encFile.getName(),
            md5Hex);
    JsonNode jsonResponse =
        Unirest.patch(uploadURL)
            .socketTimeout(1000000000)
            .basicAuth(E2EState.env.getCegaAuthUsername(), E2EState.env.getCegaAuthPassword())
            .header("Proxy-Authorization", "Bearer " + token)
            .body(FileUtils.readFileToByteArray(E2EState.encFile))
            .asJson()
            .getBody();
    String uploadId = jsonResponse.getObject().getString("id");
    E2EState.log.info("Upload ID: {}", uploadId);
    String finalizeURL =
        String.format(
            "https://%s:%s/stream/%s?uploadId=%s&chunk=end&sha256=%s&fileSize=%s",
            E2EState.env.getProxyHost(),
            E2EState.env.getProxyPort(),
            E2EState.encFile.getName(),
            uploadId,
            E2EState.encSHA256Checksum,
            FileUtils.sizeOf(E2EState.encFile));
    HttpResponse<JsonNode> res =
        Unirest.patch(finalizeURL)
            .socketTimeout(1000000)
            .basicAuth(E2EState.env.getCegaAuthUsername(), E2EState.env.getCegaAuthPassword())
            .header("Proxy-Authorization", "Bearer " + token)
            .asJson();
    jsonResponse = res.getBody();
    assertEquals(201, jsonResponse.getObject().get("statusCode"));
  }


  private static String resolveUploadToken() throws Exception {
    // if a passport scoped access token is not provided we generate a fake one
    if (E2EState.env.getLSAAIToken() == null || E2EState.env.getLSAAIToken().isEmpty()) {
      return TokenUtils.generateVisaToken("upload", "jwt.pub.pem", "jwt.priv.pem");
    } else {
      // if a passport scoped access token is provided and also the runtime is set to
      // EGA_DEV we will use that provided token to upload the file.
      if (!E2EState.env.getIntegration().equals("EGA_DEV"))
        fail("LSAAIToken provided but the runtime is not set to EGA_DEV");
      return E2EState.env.getLSAAIToken();
    }
  }

}
