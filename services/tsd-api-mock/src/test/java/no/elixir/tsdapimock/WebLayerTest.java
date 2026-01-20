package no.elixir.tsdapimock;

import static javax.management.timer.Timer.ONE_HOUR;
import static org.assertj.core.api.Assertions.assertThat;

import no.elixir.tsdapimock.core.utils.JwtService;
import no.elixir.tsdapimock.endpoints.auth.basic.Client;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebLayerTest {

  private static Client client;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeAll
  public static void setUp() {
    client = Client.builder().name("test").email("test@example.com").build();
  }

  @Nested
  class BasicAuth {
    private final String basicAuthUrl = "/v1/p-test/auth/basic";

    @Test
    public void testSignup() throws Exception {
      var requestBody =
          new ObjectMapper()
              .createObjectNode()
              .put("client_name", client.getName())
              .put("EMAIL", client.getEmail())
              .toString();

      var requestHeaders = new HttpHeaders();
      requestHeaders.setContentType(MediaType.APPLICATION_JSON);

      var requestEntity = new HttpEntity<>(requestBody, requestHeaders);

      ResponseEntity<String> response =
          restTemplate.postForEntity(basicAuthUrl + "/signup", requestEntity, String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();

      var responseJson = new ObjectMapper().readTree(response.getBody());

      var clientId = responseJson.get("client_id");
      assertThat(clientId).isNotNull();
      assertThat(clientId.textValue()).isNotBlank();

      client.setId(clientId.textValue());
    }

    @Nested
    class BasicAuthSignupConfirmation {
      @Test
      public void testSignupConfirmation() throws Exception {
        var requestBody =
            new ObjectMapper()
                .createObjectNode()
                .put("client_id", client.getId())
                .put("EMAIL", client.getEmail())
                .put("client_name", client.getName())
                .toString();

        var requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);

        var requestEntity = new HttpEntity<>(requestBody, requestHeaders);

        ResponseEntity<String> response =
            restTemplate.postForEntity(
                basicAuthUrl + "/signupconfirm", requestEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        var responseJson = new ObjectMapper().readTree(response.getBody());
        var confirmationToken = responseJson.get("confirmation_token").asText();
        assertThat(confirmationToken).isNotBlank();

        client.setConfirmationToken(confirmationToken);
      }

      @Test
      public void testGetBasicToken() throws Exception {
        var requestBody = new ObjectMapper().createObjectNode().put("type", "string").toString();

        var requestHeaders = new HttpHeaders();
        requestHeaders.setBearerAuth("123abc");
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);

        var requestEntity = new HttpEntity<>(requestBody, requestHeaders);

        ResponseEntity<String> response =
            restTemplate.postForEntity(basicAuthUrl + "/token", requestEntity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        var responseJson = new ObjectMapper().readTree(response.getBody());
        var token = responseJson.get("token").asText();
        assertThat(token).isNotBlank();
      }

      @Nested
      class BasicAuthConfirmation {
        @Test
        public void testConfirmation() throws Exception {
          var requestBody =
              new ObjectMapper()
                  .createObjectNode()
                  .put("client_id", client.getId())
                  .put("confirmation_token", client.getConfirmationToken())
                  .toString();

          var requestHeaders = new HttpHeaders();
          requestHeaders.setContentType(MediaType.APPLICATION_JSON);

          var requestEntity = new HttpEntity<>(requestBody, requestHeaders);

          ResponseEntity<String> response =
              restTemplate.postForEntity(basicAuthUrl + "/confirm", requestEntity, String.class);

          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
          assertThat(response.getBody()).isNotNull();

          var responseJson = new ObjectMapper().readTree(response.getBody());
          var password = responseJson.get("password").asText();
          assertThat(password).isNotBlank();

          client.setPassword(password);
        }

        @Nested
        class BasicAuthApiKey {
          @Test
          public void testApiKey() throws Exception {
            var requestBody =
                new ObjectMapper()
                    .createObjectNode()
                    .put("client_id", client.getId())
                    .put("password", client.getPassword())
                    .toString();

            var requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.APPLICATION_JSON);

            var requestEntity = new HttpEntity<>(requestBody, requestHeaders);

            ResponseEntity<String> response =
                restTemplate.postForEntity(basicAuthUrl + "/api_key", requestEntity, String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            var responseJson = new ObjectMapper().readTree(response.getBody());
            var apiKey = responseJson.get("api_key").asText();
            assertThat(apiKey).isNotBlank();
          }
        }
      }
    }
  }

  @Nested
  class TsdAuth {
    private final String tsdAuthUrl = "/v1/p-test/auth/tsd";

    @Test
    public void testGetTsdToken() throws Exception {
      var requestBody =
          new ObjectMapper()
              .createObjectNode()
              .put("user_name", "test")
              .put("otp", "1234")
              .put("password", "password")
              .toString();

      var requestHeaders = new HttpHeaders();
      requestHeaders.setContentType(MediaType.APPLICATION_JSON);
      requestHeaders.setBearerAuth("abc123");

      var requestEntity = new HttpEntity<>(requestBody, requestHeaders);

      ResponseEntity<String> response =
          restTemplate.postForEntity(tsdAuthUrl + "/token", requestEntity, String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      var responseJson = new ObjectMapper().readTree(response.getBody());
      var token = responseJson.get("token");
      assertThat(token).isNotNull();
      assertThat(token.textValue()).isNotBlank();
    }
  }

  @Nested
  class ElixirAuth {

    private final String elixirAuthUrl = "/v1/p-test/auth/elixir";
    private final JwtService jwtService;

    @Autowired
    ElixirAuth(JwtService jwtService) {
      this.jwtService = jwtService;
    }

    // @Test
    // FIXME:
    //
    // This test case is obsolete. The method needs to check
    // given an Elixir AAI token the returned token is a valid
    // TSD issued token. The Elixir token should be signed with
    // jwt.priv.pem and verified it with jwt.pub.pem (it should
    // be mapped to the resources or symlinked.
    //
    // And the TSD token returned must be verified back with its
    // symmetric key.
    public void testElixirToken() throws Exception {
      var jwtToken =
          jwtService.createJwt("p-test", client.getEmail(), "TSD", client.getEmail(), ONE_HOUR);
      var requestBody = new ObjectMapper().createObjectNode().put("idtoken", jwtToken).toString();

      var requestHeaders = new HttpHeaders();
      requestHeaders.setContentType(MediaType.APPLICATION_JSON);
      requestHeaders.setBearerAuth("abc123");

      var requestEntity = new HttpEntity<>(requestBody, requestHeaders);

      ResponseEntity<String> response =
          restTemplate.postForEntity(elixirAuthUrl + "/token", requestEntity, String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();

      var responseJson = new ObjectMapper().readTree(response.getBody());
      var token = responseJson.get("token");
      assertThat(token).isNotNull();
      assertThat(token.textValue()).isNotBlank();
    }
  }

  @Nested
  class Ega {

    private final String egaUrl = "/v1/p-test/ega/testUser";

    @Test
    public void testGetResumableUploads() throws Exception {
      var authHeader = "Bearer validToken";

      var requestHeaders = new HttpHeaders();
      requestHeaders.set("Authorization", authHeader);

      var requestEntity = new HttpEntity<>(requestHeaders);

      ResponseEntity<String> response =
          restTemplate.exchange(
              egaUrl + "/resumables", HttpMethod.GET, requestEntity, String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotBlank();

      var responseJson = new ObjectMapper().readTree(response.getBody());
      var resumables = responseJson.get("resumables");
      assertThat(resumables).isNotNull();
      assertThat(resumables.isArray()).isTrue();
    }
  }
}
