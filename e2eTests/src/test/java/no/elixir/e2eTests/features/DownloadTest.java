package no.elixir.e2eTests.features;

import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.State;
import no.elixir.e2eTests.utils.TokenUtils;

public class DownloadTest {

    public static void testDownloadDatasetUsingExportRequestAndVerifyResults() throws Exception {
        State.log.info("Preparing to make export request");
        String accessToken = TokenUtils.generateVisaToken(State.datasetId);
        String exportReqUrl = String.format(
                "https://%s:%s/export",
                State.env.getProxyHost(),
                State.env.getProxyPort()
        );
        String payload = Strings.EXPORT_REQ_BODY.formatted(
                State.datasetId,
                accessToken,
                TokenUtils.encodedPublicKey(),
                "DATASET_ID"
        );
        JsonNode jsonResponse =
                Unirest.post(exportReqUrl)
                        .body(payload)
                        .basicAuth(State.env.getProxyAdminUsername(), State.env.getProxyAdminPassword())
                        .asJson()
                        .getBody();
        State.log.info("Export request response: {}", jsonResponse.toString());
    }

}
