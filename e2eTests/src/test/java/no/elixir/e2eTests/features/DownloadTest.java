package no.elixir.e2eTests.features;

import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.TokenUtils;

public class DownloadTest {

  public static void testDownloadDatasetUsingExportRequestAndVerifyResults() throws Exception {
    E2EState.log.info("Preparing to make export request");
    String accessToken = TokenUtils.generateVisaToken(E2EState.datasetId);
    String exportReqUrl =
        String.format(
            "https://%s:%s/export", E2EState.env.getProxyHost(), E2EState.env.getProxyPort());
    String payload =
        Strings.EXPORT_REQ_BODY.formatted(
            E2EState.datasetId, accessToken, TokenUtils.encodedPublicKey(), "DATASET_ID");
    JsonNode jsonResponse =
        Unirest.post(exportReqUrl)
            .body(payload)
            .basicAuth(E2EState.env.getProxyAdminUsername(), E2EState.env.getProxyAdminPassword())
            .asJson()
            .getBody();
    E2EState.log.info("Export request response: {}", jsonResponse.toString());
  }
}
