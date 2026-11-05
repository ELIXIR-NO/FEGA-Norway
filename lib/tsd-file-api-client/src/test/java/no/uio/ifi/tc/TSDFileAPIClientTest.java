package no.uio.ifi.tc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import no.uio.ifi.tc.model.pojo.Message;
import no.uio.ifi.tc.model.pojo.TSDFileAPIResponse;
import no.uio.ifi.tc.model.pojo.TSDFiles;
import no.uio.ifi.tc.model.pojo.Token;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TSDFileAPIClientTest {

  private static final String APP_ID = "ega";
  private static final String TSD_TOKEN =
      "header."
          + Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString("{\"user\":\"p11-user\"}".getBytes(StandardCharsets.UTF_8))
          + ".signature";

  private MockWebServer tsd;
  private TSDFileAPIClient client;

  @BeforeEach
  void setUp() throws IOException {
    tsd = new MockWebServer();
    tsd.start();
    client =
        new TSDFileAPIClient.Builder()
            .host(tsd.getHostName() + ":" + tsd.getPort())
            .accessKey("access-key")
            .build();
  }

  @AfterEach
  void tearDown() throws IOException {
    tsd.shutdown();
  }

  @Test
  void listFilesReturnsTheFilesOn2xx() throws IOException {
    tsd.enqueue(json(200, "{\"files\":[{\"filename\":\"a.c4gh\"}],\"page\":null}"));

    TSDFiles files = client.listFiles(TSD_TOKEN, APP_ID, 1, 50);

    assertEquals(200, files.getStatusCode());
    assertEquals(1, files.getFiles().size());
  }

  @Test
  void listFilesReportsTsdStatusWithoutParsingAnErrorBody() throws IOException {
    tsd.enqueue(json(404, "{\"message\":\"not found\"}"));

    TSDFiles files = client.listFiles(TSD_TOKEN, APP_ID, 1, 50);

    assertEquals(404, files.getStatusCode());
    assertNull(files.getFiles());
  }

  @Test
  void listFilesFailsOnA2xxBodyThatIsNotJson() {
    tsd.enqueue(new MockResponse().setResponseCode(200).setBody("<html>gateway</html>"));

    assertThrows(IOException.class, () -> client.listFiles(TSD_TOKEN, APP_ID, 1, 50));
  }

  @Test
  void listFilesPropagatesAConnectionFailure() throws IOException {
    tsd.shutdown();

    assertThrows(IOException.class, () -> client.listFiles(TSD_TOKEN, APP_ID, 1, 50));
  }

  @Test
  void getTokenReturnsTheTokenOn2xx() throws IOException {
    tsd.enqueue(json(200, "{\"token\":\"" + TSD_TOKEN + "\"}"));

    Token token = client.getToken("TOKEN", "elixir", "id-token");

    assertEquals(200, token.getStatusCode());
    assertEquals(TSD_TOKEN, token.getToken());
  }

  @Test
  void getTokenReportsTsdStatusAndNoToken() throws IOException {
    tsd.enqueue(json(401, "{\"message\":\"bad token\"}"));

    Token token = client.getToken("TOKEN", "elixir", "id-token");

    assertEquals(401, token.getStatusCode());
    assertNull(token.getToken());
  }

  @Test
  void getTokenPropagatesAConnectionFailure() throws IOException {
    tsd.shutdown();

    assertThrows(IOException.class, () -> client.getToken("TOKEN", "elixir", "id-token"));
  }

  @Test
  void uploadFileReportsTsdStatusOn5xx() throws IOException {
    tsd.enqueue(new MockResponse().setResponseCode(500).setBody("not json"));

    Message message =
        client.uploadFile(TSD_TOKEN, APP_ID, new ByteArrayInputStream(new byte[] {1}), "a.c4gh");

    assertEquals(500, message.getStatusCode());
  }

  @Test
  void getResumableUploadFailsWhenTsdCannotListResumables() {
    tsd.enqueue(json(503, "{}"));

    assertThrows(IOException.class, () -> client.getResumableUpload(TSD_TOKEN, APP_ID, "id"));
  }

  @Test
  void downloadFileWritesNothingOnANon2xx() throws IOException {
    tsd.enqueue(new MockResponse().setResponseCode(404).setBody("missing"));
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    TSDFileAPIResponse response = client.downloadFile(TSD_TOKEN, APP_ID, "a.c4gh", out);

    assertEquals(404, response.getStatusCode());
    assertEquals(0, out.size());
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
