package no.elixir.tsdapimock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxListingTest {

  private static final String PROJECT = "p-test";
  private static final String USER = "testUser";
  private static final String EMPTY_USER = "emptyUser";

  @TempDir static Path outboxRoot;

  @Autowired private TestRestTemplate restTemplate;

  @DynamicPropertySource
  static void exportDirectory(DynamicPropertyRegistry registry) {
    registry.add("tsd.elixir.export", () -> outboxRoot + "/%s/%s/files/");
  }

  @BeforeAll
  static void fillOutbox() throws Exception {
    Path outbox =
        Files.createDirectories(outboxRoot.resolve(PROJECT).resolve(USER).resolve("files"));
    for (String name : List.of("c.c4gh", "a.c4gh", "e.c4gh", "b.c4gh", "d.c4gh")) {
      Files.writeString(outbox.resolve(name), name);
    }
    Files.createDirectories(outbox.resolve("folder"));
    Files.createDirectories(outboxRoot.resolve(PROJECT).resolve(EMPTY_USER).resolve("files"));
  }

  @Test
  void listsEveryFileWithoutPaginationParameters() throws Exception {
    JsonNode listing = listing(USER, "");

    assertThat(fileNames(listing))
        .containsExactly("a.c4gh", "b.c4gh", "c.c4gh", "d.c4gh", "e.c4gh");
    assertThat(listing.get("page").isNull()).isTrue();
  }

  @Test
  void followsNextPageLinksToTheLastPage() throws Exception {
    JsonNode first = listing(USER, "?page=0&per_page=2");
    assertThat(fileNames(first)).containsExactly("a.c4gh", "b.c4gh");
    assertThat(first.get("page").asString()).isEqualTo(url(USER) + "?page=1");

    JsonNode second = listing(USER, "?page=1&per_page=2");
    assertThat(fileNames(second)).containsExactly("c.c4gh", "d.c4gh");
    assertThat(second.get("page").asString()).isEqualTo(url(USER) + "?page=2");

    JsonNode last = listing(USER, "?page=2&per_page=2");
    assertThat(fileNames(last)).containsExactly("e.c4gh");
    assertThat(last.get("page").isNull()).isTrue();
  }

  @Test
  void aFullLastPageCarriesNoNextLink() throws Exception {
    JsonNode listing = listing(USER, "?page=0&per_page=5");

    assertThat(fileNames(listing)).hasSize(5);
    assertThat(listing.get("page").isNull()).isTrue();
  }

  @Test
  void aPageBeyondTheEndIsEmpty() throws Exception {
    for (String query :
        List.of(
            "?page=3&per_page=2",
            "?page=0&per_page=0",
            "?page=9223372036854775807&per_page=50000")) {
      JsonNode listing = listing(USER, query);

      assertThat(fileNames(listing)).isEmpty();
      assertThat(listing.get("page").isNull()).isTrue();
    }
  }

  @Test
  void perPageIsIgnoredWithoutPage() throws Exception {
    JsonNode listing = listing(USER, "?per_page=2");

    assertThat(fileNames(listing)).hasSize(5);
    assertThat(listing.get("page").isNull()).isTrue();
  }

  @Test
  void anEmptyOutboxListsNothing() throws Exception {
    JsonNode listing = listing(EMPTY_USER, "?page=0&per_page=50000");

    assertThat(fileNames(listing)).isEmpty();
    assertThat(listing.get("page").isNull()).isTrue();
  }

  @Test
  void acceptsTheLargestPageSize() {
    assertThat(get(USER, "?page=0&per_page=50000").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void rejectsInvalidPaginationParameters() {
    for (String query :
        List.of("?page=0&per_page=50001", "?page=-1&per_page=2", "?page=x", "?page=0&per_page=x")) {
      assertThat(get(USER, query).getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  private static String url(String user) {
    return "/v1/" + PROJECT + "/egaout/" + user + "/files";
  }

  private ResponseEntity<String> get(String user, String query) {
    var headers = new HttpHeaders();
    headers.setBearerAuth("validToken");
    return restTemplate.exchange(
        url(user) + query, HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private JsonNode listing(String user, String query) throws Exception {
    ResponseEntity<String> response = get(user, query);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return new ObjectMapper().readTree(response.getBody());
  }

  private static List<String> fileNames(JsonNode listing) {
    return listing.get("files").valueStream().map(file -> file.get("filename").asString()).toList();
  }
}
