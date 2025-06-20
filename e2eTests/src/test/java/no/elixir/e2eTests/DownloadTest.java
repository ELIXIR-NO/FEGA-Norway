package no.elixir.e2eTests;

import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import no.elixir.e2eTests.constants.Strings;
import org.junit.jupiter.api.Test;

public class DownloadTest extends BaseE2ETest {

    @Test
    public void testDownloadDatasetUsingExportRequestAndVerifyResults() throws Exception {
        setupTestEnvironment();
        try {
            test();
        } finally {
            cleanupTestEnvironment();
        }
    }

    private void test() throws Exception {
        log.info("Preparing to make export request");
        String accessToken = generateVisaToken(datasetId);
        String exportReqUrl = String.format(
                "https://%s:%s/export",
                env.getProxyHost(),
                env.getProxyPort()
        );
        String payload = Strings.EXPORT_REQ_BODY.formatted(
                datasetId,
                accessToken,
                encodedPublicKey(),
                "DATASET_ID"
        );
        JsonNode jsonResponse =
                Unirest.post(exportReqUrl)
                        .body(payload)
                        .basicAuth(env.getProxyAdminUsername(), env.getProxyAdminPassword())
                        .asJson()
                        .getBody();
        log.info("Export request response: {}", jsonResponse.toString());
    }

}
