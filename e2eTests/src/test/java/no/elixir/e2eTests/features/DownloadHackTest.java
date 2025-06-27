package no.elixir.e2eTests.features;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import no.elixir.crypt4gh.stream.Crypt4GHInputStream;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.State;
import no.elixir.e2eTests.utils.CommonUtils;
import no.elixir.e2eTests.utils.TokenUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DownloadHackTest {

    /**
     * Test and check that what we get out match the
     * original inserted data at the top.
     */
    public static void downloadDatasetAndVerifyResults() throws Exception {
        String token = TokenUtils.generateVisaToken(State.datasetId);
        State.log.info("Visa JWT token when downloading: {}", token);
        String datasets =
                Unirest.get(
                                String.format(
                                        "https://%s:%s/metadata/datasets", State.env.getSdaDoaHost(), State.env.getSdaDoaPort()))
                        .header("Authorization", "Bearer " + token)
                        .asString()
                        .getBody();
        assertEquals(String.format("[\"%s\"]", State.datasetId).strip(), datasets.strip());
        // Meta data check
        String expected =
                CommonUtils.toCompactJson(
                        String.format(
                                        Strings.EXPECTED_DOWNLOAD_METADATA,
                                        State.stableId,
                                        State.datasetId,
                                        State.encFile.getName(),
                                        State.archivePath,
                                        State.rawSHA256Checksum)
                                .strip());
        String actual =
                CommonUtils.toCompactJson(
                        Unirest.get(
                                        String.format(
                                                "https://%s:%s/metadata/datasets/%s/files",
                                                State.env.getSdaDoaHost(), State.env.getSdaDoaPort(), State.datasetId))
                                .header("Authorization", "Bearer " + token)
                                .asString()
                                .getBody()
                                .strip());
        State.log.info("Expected: {}", expected);
        State.log.info("Actual: {}", actual);
        JSONAssert.assertEquals(expected, actual, false);
        // Fetch the non-encrypted file
        HttpResponse<byte[]> response =
                Unirest.get(
                                String.format(
                                        "https://%s:%s/files/%s", State.env.getSdaDoaHost(), State.env.getSdaDoaPort(), State.stableId))
                        .header("Authorization", "Bearer " + token)
                        .asBytes();
        if (response.getStatus() == 200) { // Check if the response is OK
            byte[] file = response.getBody();
            String obtainedChecksum = Hex.encodeHexString(DigestUtils.sha256(file));
            assertEquals(State.rawSHA256Checksum, obtainedChecksum);
        } else {
            fail("Failed to fetch the file. Status: " + response.getStatus());
        }
        // Fetch the encrypted file
        KeyPair recipientKeyPair = State.keyUtils.generateKeyPair();
        StringWriter stringWriter = new StringWriter();
        State.keyUtils.writeCrypt4GHKey(stringWriter, recipientKeyPair.getPublic(), null);
        String key = stringWriter.toString();
        HttpResponse<byte[]> encFileRes =
                Unirest.get(
                                String.format(
                                        "https://%s:%s/files/%s?destinationFormat=CRYPT4GH",
                                        State.env.getSdaDoaHost(), State.env.getSdaDoaPort(), State.stableId))
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
            assertEquals(State.rawSHA256Checksum, obtainedChecksum);
        } else {
            fail("Failed to fetch the encrypted file. Status: " + response.getStatus());
        }
    }

}
