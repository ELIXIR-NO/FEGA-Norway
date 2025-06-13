package no.elixir.e2eTests;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import no.elixir.crypt4gh.stream.Crypt4GHInputStream;
import no.elixir.e2eTests.constants.Strings;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.security.KeyPair;

import static no.elixir.e2eTests.utils.JsonUtils.toCompactJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DownloadVerificationTest extends BaseE2ETest {

    @Test
    public void testDownloadDatasetAndVerifyResults() throws Exception {
        setupTestEnvironment();
        try {
            // Test and check that what we get out match
            // the original inserted data at the top
            downloadDatasetAndVerifyResults();
        } finally {
            cleanupTestEnvironment();
        }
    }

    private void downloadDatasetAndVerifyResults() throws Exception {
        String token = generateVisaToken(datasetId);
        log.info("Visa JWT token when downloading: {}", token);
        String datasets =
                Unirest.get(
                                String.format(
                                        "https://%s:%s/metadata/datasets", env.getSdaDoaHost(), env.getSdaDoaPort()))
                        .header("Authorization", "Bearer " + token)
                        .asString()
                        .getBody();
        assertEquals(String.format("[\"%s\"]", datasetId).strip(), datasets.strip());
        // Meta data check
        String expected =
                toCompactJson(
                        String.format(
                                        Strings.EXPECTED_DOWNLOAD_METADATA,
                                        stableId,
                                        datasetId,
                                        encFile.getName(),
                                        archivePath,
                                        rawSHA256Checksum)
                                .strip());
        String actual =
                toCompactJson(
                        Unirest.get(
                                        String.format(
                                                "https://%s:%s/metadata/datasets/%s/files",
                                                env.getSdaDoaHost(), env.getSdaDoaPort(), datasetId))
                                .header("Authorization", "Bearer " + token)
                                .asString()
                                .getBody()
                                .strip());
        log.info("Expected: {}", expected);
        log.info("Actual: {}", actual);
        JSONAssert.assertEquals(expected, actual, false);
        // Fetch the non-encrypted file
        HttpResponse<byte[]> response =
                Unirest.get(
                                String.format(
                                        "https://%s:%s/files/%s", env.getSdaDoaHost(), env.getSdaDoaPort(), stableId))
                        .header("Authorization", "Bearer " + token)
                        .asBytes();
        if (response.getStatus() == 200) { // Check if the response is OK
            byte[] file = response.getBody();
            String obtainedChecksum = Hex.encodeHexString(DigestUtils.sha256(file));
            assertEquals(rawSHA256Checksum, obtainedChecksum);
        } else {
            fail("Failed to fetch the file. Status: " + response.getStatus());
        }
        // Fetch the encrypted file
        KeyPair recipientKeyPair = keyUtils.generateKeyPair();
        StringWriter stringWriter = new StringWriter();
        keyUtils.writeCrypt4GHKey(stringWriter, recipientKeyPair.getPublic(), null);
        String key = stringWriter.toString();
        HttpResponse<byte[]> encFileRes =
                Unirest.get(
                                String.format(
                                        "https://%s:%s/files/%s?destinationFormat=CRYPT4GH",
                                        env.getSdaDoaHost(), env.getSdaDoaPort(), stableId))
                        .header("Authorization", "Bearer " + token)
                        .header("Public-Key", key)
                        .asBytes();
        if (encFileRes.getStatus() == 200) { // Check if the response is OK
            byte[] file = encFileRes.getBody();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(file);
                 Crypt4GHInputStream crypt4GHInputStream =
                         new Crypt4GHInputStream(byteArrayInputStream, recipientKeyPair.getPrivate())) {
                IOUtils.copyLarge(crypt4GHInputStream, byteArrayOutputStream);
            }
            String obtainedChecksum =
                    Hex.encodeHexString(DigestUtils.sha256(byteArrayOutputStream.toByteArray()));
            assertEquals(rawSHA256Checksum, obtainedChecksum);
        } else {
            fail("Failed to fetch the encrypted file. Status: " + response.getStatus());
        }
    }

}
