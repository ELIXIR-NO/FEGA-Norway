package no.elixir.e2eTests.features;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import no.elixir.e2eTests.core.State;
import no.elixir.e2eTests.utils.CommonUtils;
import no.elixir.e2eTests.utils.TokenUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UploadTest {

    public static void uploadThroughProxy() throws Exception {
        State.log.info("Uploading a file through a proxy...");
        String token = TokenUtils.generateVisaToken("upload");
        State.log.info("Visa JWT token when uploading: {}", token);
        String md5Hex = DigestUtils.md5Hex(Files.newInputStream(State.encFile.toPath()));
        State.log.info("Encrypted MD5 checksum: {}", md5Hex);
        State.log.info("Cega Auth Username: {}", State.env.getCegaAuthUsername());
        String uploadURL =
                String.format(
                        "https://%s:%s/stream/%s?md5=%s",
                        State.env.getProxyHost(), State.env.getProxyPort(), State.encFile.getName(), md5Hex);
        JsonNode jsonResponse =
                Unirest.patch(uploadURL)
                        .socketTimeout(1000000000)
                        .basicAuth(State.env.getCegaAuthUsername(), State.env.getCegaAuthPassword())
                        .header("Proxy-Authorization", "Bearer " + token)
                        .body(FileUtils.readFileToByteArray(State.encFile))
                        .asJson()
                        .getBody();
        String uploadId = jsonResponse.getObject().getString("id");
        State.log.info("Upload ID: {}", uploadId);
        String finalizeURL =
                String.format(
                        "https://%s:%s/stream/%s?uploadId=%s&chunk=end&sha256=%s&fileSize=%s",
                        State.env.getProxyHost(),
                        State.env.getProxyPort(),
                        State.encFile.getName(),
                        uploadId,
                        State.encSHA256Checksum,
                        FileUtils.sizeOf(State.encFile));
        HttpResponse<JsonNode> res =
                Unirest.patch(finalizeURL)
                        .socketTimeout(1000000)
                        .basicAuth(State.env.getCegaAuthUsername(), State.env.getCegaAuthPassword())
                        .header("Proxy-Authorization", "Bearer " + token)
                        .asJson();
        jsonResponse = res.getBody();
        assertEquals(201, jsonResponse.getObject().get("statusCode"));

    }

}
