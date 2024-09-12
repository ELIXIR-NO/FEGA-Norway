package no.elixir.tsdapimock;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FileHandlingTest {

  @Autowired private TestRestTemplate restTemplate;

  private final String filesUrl = "/v1/p-test/files";

  @Test
  public void testFilesUpload() throws Exception {
    System.out.println();
    byte[] fileContent = "test content".getBytes();

    var authHeader = "Bearer validToken";

    var requestHeaders = new HttpHeaders();
    requestHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    requestHeaders.set("Authorization", authHeader);

    String uploadFileName = "testFile.txt";
    requestHeaders.set("filename", uploadFileName);

    var requestEntity = new HttpEntity<>(fileContent, requestHeaders);

    ResponseEntity<String> response =
        restTemplate.exchange(filesUrl + "/stream", HttpMethod.PUT, requestEntity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotBlank();

    var responseJson = new ObjectMapper().readTree(response.getBody());
    var message = responseJson.get("message");
    assertThat(message.textValue()).isNotBlank();
    System.out.println(message);
  }

  @Test
  public void testFilesCreateFolder() throws Exception {
    var authHeader = "Bearer validToken";
    var folderName = "testFolder";

    var requestHeaders = new HttpHeaders();
    requestHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    requestHeaders.set("Authorization", authHeader);

    var requestEntity = new HttpEntity<>(null, requestHeaders);

    ResponseEntity<String> response =
        restTemplate.exchange(
            filesUrl + "/folder" + "?name=" + URLEncoder.encode(folderName, StandardCharsets.UTF_8),
            HttpMethod.PUT,
            requestEntity,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotBlank();

    var responseJson = new ObjectMapper().readTree(response.getBody());
    var message = responseJson.get("message");
    assertThat(message.textValue()).isNotBlank();
  }

  @Test
  public void testGetResumableUploads() throws Exception {
    var authHeader = "Bearer validToken";

    var requestHeaders = new HttpHeaders();
    requestHeaders.set("Authorization", authHeader);

    var requestEntity = new HttpEntity<>(requestHeaders);

    ResponseEntity<String> response =
        restTemplate.exchange(
            filesUrl + "/resumables", HttpMethod.GET, requestEntity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotBlank();

    var responseJson = new ObjectMapper().readTree(response.getBody());
    var resumables = responseJson.get("resumables");
    assertThat(resumables).isNotNull();
    assertThat(resumables.isArray()).isTrue();
  }

  //     TODO: Find a way to test
  //          1. resumable uploads in files
  //          2. resumable uploads in ega
  //          3. delete uploaded file (needs ID not provided by this repo)
  //
  //    @Test
  //    public void testHandleResumableUpload() throws Exception {
  //      byte[] firstChunkContent = "First chunk content".getBytes();
  //      byte[] secondChunkContent = "Second chunk content".getBytes();
  //      byte[] finalChunkContent = "Final chunk content".getBytes();
  //
  //      var authHeader = "Bearer validToken";
  //      var fileName = "resumableFile.txt";
  //
  //      uploadChunk(fileName, firstChunkContent, authHeader, "1", null);
  //
  //      var uploadId = "someGeneratedUploadId";
  //      uploadChunk(fileName, secondChunkContent, authHeader, "2", uploadId);
  //
  //      ResponseEntity<String> finalResponse =
  //          uploadChunk(fileName, finalChunkContent, authHeader, "end", uploadId);
  //
  //      assertThat(finalResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  //      assertThat(finalResponse.getBody()).isNotBlank();
  //
  //      var responseJson = new ObjectMapper().readTree(finalResponse.getBody());
  //      var message = responseJson.get("message");
  //      assertThat(message.textValue()).isNotBlank();
  //    }
  //
  //    private ResponseEntity<String> uploadChunk(
  //        String fileName, byte[] content, String authHeader, String chunk, String uploadId) {
  //      var requestHeaders = new HttpHeaders();
  //      requestHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
  //      //      requestHeaders.set("X-HTTP-Method-override", "PATCH");
  //      requestHeaders.set("Authorization", authHeader);
  //      requestHeaders.set("filename", fileName);
  //      if (uploadId != null) {
  //        requestHeaders.set("id", uploadId);
  //      }
  //
  //      var requestEntity = new HttpEntity<>(content, requestHeaders);
  //      return restTemplate.exchange(
  //          filesUrl + "/stream/" + fileName + "?chunk=" + chunk,
  //          HttpMethod.PATCH,
  //          requestEntity,
  //          String.class);
  //    }
  //
  //    @Nested
  //    class DeleteResumable {
  //      @Test
  //      public void testDeleteResumableUpload() throws Exception {
  //        String authHeader = "Bearer validToken";
  //
  //        var requestHeaders = new HttpHeaders();
  //        requestHeaders.set("Authorization", authHeader);
  //
  //        var requestEntity = new HttpEntity<>(requestHeaders);
  //
  //        ResponseEntity<String> response =
  //            restTemplate.exchange(
  //                filesUrl + "/resumables/" + uploadFileName + "?id=" + uploadId,
  //                HttpMethod.DELETE,
  //                requestEntity,
  //                String.class);
  //
  //        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  //        assertThat(response.getBody()).isNotBlank();
  //
  //        var responseJson = new ObjectMapper().readTree(response.getBody());
  //        var message = responseJson.get("message").textValue();
  //        assertThat(message).isEqualTo("Resumable deleted");
  //      }
  //    }

}
