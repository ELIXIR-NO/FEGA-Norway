package no.elixir.e2eTests;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UploadTest extends BaseE2ETest {

    @Test
    public void testUpload() throws Exception {
        setupTestEnvironment();
        try {
            test();
            // Wait for triggers to be set up at CEGA.
            // Not really needed if using local CEGA container.
            waitForProcessing(5000);
        } finally {
            cleanupTestEnvironment();
        }
    }

    private void test() throws Exception {
        log.info("Uploading a file through a proxy...");
        String token = generateVisaToken("upload");
        log.info("Visa JWT token when uploading: {}", token);
        String md5Hex = DigestUtils.md5Hex(Files.newInputStream(encFile.toPath()));
        log.info("Encrypted MD5 checksum: {}", md5Hex);
        log.info("Cega Auth Username: {}", env.getCegaAuthUsername());
        String uploadURL =
                String.format(
                        "https://%s:%s/stream/%s?md5=%s",
                        env.getProxyHost(), env.getProxyPort(), encFile.getName(), md5Hex);
        JsonNode jsonResponse =
                Unirest.patch(uploadURL)
                        .socketTimeout(1000000000)
                        .basicAuth(env.getCegaAuthUsername(), env.getCegaAuthPassword())
                        .header("Proxy-Authorization", "Bearer " + token)
                        .body(FileUtils.readFileToByteArray(encFile))
                        .asJson()
                        .getBody();
        String uploadId = jsonResponse.getObject().getString("id");
        log.info("Upload ID: {}", uploadId);
        String finalizeURL =
                String.format(
                        "https://%s:%s/stream/%s?uploadId=%s&chunk=end&sha256=%s&fileSize=%s",
                        env.getProxyHost(),
                        env.getProxyPort(),
                        encFile.getName(),
                        uploadId,
                        encSHA256Checksum,
                        FileUtils.sizeOf(encFile));
        HttpResponse<JsonNode> res =
                Unirest.patch(finalizeURL)
                        .socketTimeout(1000000)
                        .basicAuth(env.getCegaAuthUsername(), env.getCegaAuthPassword())
                        .header("Proxy-Authorization", "Bearer " + token)
                        .asJson();
        jsonResponse = res.getBody();
        assertEquals(201, jsonResponse.getObject().get("statusCode"));
    }

}
