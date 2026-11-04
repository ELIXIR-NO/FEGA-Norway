package no.elixir.fega.ltp.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import no.elixir.fega.ltp.aspects.AuthAspect;
import no.elixir.fega.ltp.aspects.CredentialsMappingAspect;
import no.elixir.fega.ltp.aspects.ProcessArgumentsAspect;
import no.elixir.fega.ltp.aspects.PublishMQAspect;
import no.elixir.fega.ltp.authentication.CEGACredentialsProvider;
import no.elixir.fega.ltp.config.SecurityConfig;
import no.elixir.fega.ltp.controllers.rest.ExportRequestController;
import no.elixir.fega.ltp.controllers.rest.ProxyController;
import no.elixir.fega.ltp.dto.Credentials;
import no.elixir.fega.ltp.services.ExportRequestService;
import no.elixir.fega.ltp.services.JsonSchemaValidationService;
import no.elixir.fega.ltp.services.TokenService;
import no.uio.ifi.tc.TSDFileAPIClient;
import no.uio.ifi.tc.model.pojo.Chunk;
import no.uio.ifi.tc.model.pojo.Message;
import no.uio.ifi.tc.model.pojo.ResumableUpload;
import no.uio.ifi.tc.model.pojo.TSDFile;
import no.uio.ifi.tc.model.pojo.TSDFileAPIResponse;
import no.uio.ifi.tc.model.pojo.TSDFiles;
import no.uio.ifi.tc.model.pojo.Token;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;

/**
 * One test per {@link ErrorCode}: the status, the problem body and its members, driven through the
 * real aspects, controllers, advice and security chain. See ELIXIR-NO/FEGA-Norway#913.
 */
@WebMvcTest
@TestPropertySource(
    properties = {
      "spring.security.user.name=test-admin",
      "spring.security.user.password=test-admin-password",
      "elixir.client.id=test-client",
      "mq.tsd.exchange=sda",
      "mq.tsd.export-requests-routing-key=exportRequests",
      "mq.tsd.inbox-routing-key=inbox",
    })
class ErrorContractTest {

  // Its own configuration rather than the application class, whose test-client annotation and
  // OAuth2 login chain have no place in a MockMvc slice.
  @Configuration
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  @EnableWebSecurity
  @Import({
    ProxyController.class,
    ExportRequestController.class,
    ProblemAdvice.class,
    RequestIdFilter.class,
    AuthAspect.class,
    ProcessArgumentsAspect.class,
    CredentialsMappingAspect.class,
    PublishMQAspect.class,
    SecurityConfig.class,
    ExportRequestService.class
  })
  static class ProxySlice {}

  private static final String LS_AAI_TOKEN = "header.payload.signature";
  private static final String TSD_TOKEN = "tsd-token";
  private static final String EGA_USER = "ega-user";
  private static final String EGA_PASSWORD = "ega-password";
  private static final String ELIXIR_ID = "user@lifescience-ri.eu";
  private static final String LEAK = "SENTINEL-LEAK-TEXT";
  private static final String SHA256 = "a".repeat(64);

  // Members a problem may carry; anything else would be leaked state.
  private static final List<String> PROBLEM_MEMBERS =
      List.of("type", "title", "status", "detail", "instance", "code", "requestId");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private TSDFileAPIClient tsd;
  @MockitoBean private CEGACredentialsProvider cega;
  @MockitoBean private TokenService tokenService;
  @MockitoBean private RabbitTemplate rabbitTemplate;
  @MockitoBean private JdbcTemplate jdbcTemplate;
  @MockitoBean private JsonSchemaValidationService jsonSchemaValidationService;

  @BeforeEach
  void validCredentialsAndTsd() throws Exception {
    Claims claims = Jwts.claims().subject(ELIXIR_ID).audience().add("test-client").and().build();
    when(tokenService.parseVerified(LS_AAI_TOKEN)).thenReturn(claims);
    when(tokenService.getControlledAccessGrantsVisas(LS_AAI_TOKEN)).thenReturn(List.of());

    Credentials credentials = new Credentials();
    credentials.setPasswordHash(BCrypt.hashpw(EGA_PASSWORD, BCrypt.gensalt(4)));
    when(cega.getCredentials(EGA_USER)).thenReturn(credentials);

    when(tsd.getToken(anyString(), anyString(), eq(LS_AAI_TOKEN))).thenReturn(token(200));
    when(jdbcTemplate.queryForList(anyString(), eq(String.class), any()))
        .thenReturn(List.of(ELIXIR_ID));
  }

  @Test
  void missingLsAaiTokenIsMissingCredentialsNotASpring400() throws Exception {
    assertProblem(
        mvc.perform(get("/files").header(HttpHeaders.AUTHORIZATION, egaBasic())),
        ErrorCode.MISSING_CREDENTIALS);
    verifyNoInteractions(tsd);
  }

  @Test
  void missingEgaCredentialsIsMissingCredentials() throws Exception {
    assertProblem(
        mvc.perform(get("/files").header(HttpHeaders.PROXY_AUTHORIZATION, bearer())),
        ErrorCode.MISSING_CREDENTIALS);
    verifyNoInteractions(tsd);
  }

  @Test
  void forgedLsAaiTokenIsTokenInvalidWithABearerChallenge() throws Exception {
    when(tokenService.parseVerified(LS_AAI_TOKEN)).thenThrow(tokenInvalid());

    MvcResult result =
        assertProblem(
            mvc.perform(withCredentials(get("/files"))), ErrorCode.ELIXIR_AAI_TOKEN_INVALID);

    assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
        .isEqualTo("Bearer error=\"invalid_token\"");
    verifyNoInteractions(tsd);
  }

  @Test
  void unknownEgaUserIsEgaAuthFailed() throws Exception {
    when(cega.getCredentials(EGA_USER))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                ("No info for that user " + LEAK).getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

    assertProblem(mvc.perform(withCredentials(get("/files"))), ErrorCode.EGA_AUTH_FAILED);
  }

  @Test
  void wrongEgaPasswordIsEgaAuthFailedWithTheSameDetail() throws Exception {
    assertProblem(
        mvc.perform(
            get("/files")
                .header(HttpHeaders.PROXY_AUTHORIZATION, bearer())
                .header(HttpHeaders.AUTHORIZATION, basic(EGA_USER, "wrong-password"))),
        ErrorCode.EGA_AUTH_FAILED);
  }

  @Test
  void cegaOutageIsCegaUnavailable() throws Exception {
    when(cega.getCredentials(EGA_USER))
        .thenThrow(new ResourceAccessException("I/O error: Connection refused " + LEAK));

    assertProblem(mvc.perform(withCredentials(get("/files"))), ErrorCode.CEGA_UNAVAILABLE);
  }

  @Test
  void failedTsdListingIsUpstreamFailedNeverAnEmptyList() throws Exception {
    when(tsd.listFiles(eq(TSD_TOKEN), anyString(), anyInt(), anyInt()))
        .thenReturn(listing(500, null));

    assertProblem(mvc.perform(withCredentials(get("/files"))), ErrorCode.UPSTREAM_FAILED);
  }

  /**
   * TSD answering 404 for a user folder that was never created. Held at UPSTREAM_FAILED until TSD's
   * answer for a missing folder is confirmed to differ from a broken mount, see #913.
   */
  @Test
  void listingAMissingTsdFolderIsFolderNotFound() throws Exception {
    when(tsd.listFiles(eq(TSD_TOKEN), anyString(), anyInt(), anyInt()))
        .thenReturn(listing(404, null));

    assertProblem(mvc.perform(withCredentials(get("/files"))), ErrorCode.FOLDER_NOT_FOUND);
  }

  @Test
  void failedTsdTokenExchangeIsUpstreamFailedNotATokenError() throws Exception {
    when(tsd.getToken(anyString(), anyString(), eq(LS_AAI_TOKEN))).thenReturn(token(401));

    assertProblem(mvc.perform(withCredentials(get("/files"))), ErrorCode.UPSTREAM_FAILED);
  }

  @Test
  void unreachableTokenKeysAreUpstreamFailed() throws Exception {
    when(tokenService.parseVerified(LS_AAI_TOKEN))
        .thenThrow(
            new ProxyException(
                ErrorCode.UPSTREAM_FAILED,
                "JWKS unreachable",
                new IOException("Unable to reach OpenID JWKS endpoint " + LEAK)));

    assertProblem(mvc.perform(withCredentials(get("/files"))), ErrorCode.UPSTREAM_FAILED);
  }

  @Test
  void emptyListingIsA200() throws Exception {
    when(tsd.listFiles(eq(TSD_TOKEN), anyString(), anyInt(), anyInt()))
        .thenReturn(listing(200, List.of()));

    MvcResult result = mvc.perform(withCredentials(get("/files"))).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(result)).containsEntry("files", List.of());
  }

  @Test
  void outboxListingNeedsNoEgaCredentialsButInboxDoes() throws Exception {
    when(tsd.listFiles(eq(TSD_TOKEN), eq("egaout"), anyInt(), anyInt()))
        .thenReturn(listing(200, List.of()));

    MvcResult outbox =
        mvc.perform(
                get("/files")
                    .param("inbox", "false")
                    .header(HttpHeaders.PROXY_AUTHORIZATION, bearer()))
            .andReturn();
    assertThat(outbox.getResponse().getStatus()).isEqualTo(200);

    assertProblem(
        mvc.perform(
            get("/files").param("inbox", "true").header(HttpHeaders.PROXY_AUTHORIZATION, bearer())),
        ErrorCode.MISSING_CREDENTIALS);
  }

  /** TSD's 404 for a download used to be ignored, answering 200 with an empty attachment. */
  @Test
  void downloadingAMissingFileIsFileNotFound() throws Exception {
    when(tsd.downloadFile(eq(TSD_TOKEN), eq("egaout"), eq("a.c4gh"), any(OutputStream.class)))
        .thenReturn(status(new TSDFileAPIResponse(), 404));

    assertProblem(
        mvc.perform(get("/stream/a.c4gh").header(HttpHeaders.PROXY_AUTHORIZATION, bearer())),
        ErrorCode.FILE_NOT_FOUND);
  }

  /**
   * Once the 200 and some bytes are out, the failure must reach the container so it drops the
   * connection; answering it as handled would end the chunked body cleanly on a truncated file.
   */
  @Test
  void downloadFailingAfterTheFirstBytesIsNotEndedAsAComplete200() throws Exception {
    when(tsd.downloadFile(eq(TSD_TOKEN), eq("egaout"), eq("a.c4gh"), any(OutputStream.class)))
        .thenAnswer(
            invocation -> {
              OutputStream out = invocation.getArgument(3, OutputStream.class);
              out.write("first-segment".getBytes(StandardCharsets.UTF_8));
              out.flush();
              throw new IOException("TSD connection reset " + LEAK);
            });

    assertThatThrownBy(
            () ->
                mvc.perform(
                    get("/stream/a.c4gh").header(HttpHeaders.PROXY_AUTHORIZATION, bearer())))
        .hasStackTraceContaining("TSD connection reset");
  }

  @Test
  void clientAbortIsNotReportedAsAnUpstreamFailure() throws Exception {
    when(tsd.listFiles(eq(TSD_TOKEN), anyString(), anyInt(), anyInt()))
        .thenThrow(new ClientAbortException("Broken pipe"));

    MvcResult result = mvc.perform(withCredentials(get("/files"))).andReturn();

    assertThat(result.getResponse().getStatus()).isNotEqualTo(502);
    assertThat(result.getResponse().getContentAsString()).isEmpty();
  }

  @Test
  void downloadStreamsTheFileAsAnAttachment() throws Exception {
    when(tsd.downloadFile(eq(TSD_TOKEN), eq("egaout"), eq("a.c4gh"), any(OutputStream.class)))
        .thenAnswer(
            invocation -> {
              invocation
                  .getArgument(3, OutputStream.class)
                  .write("crypt4gh".getBytes(StandardCharsets.UTF_8));
              return status(new TSDFileAPIResponse(), 200);
            });

    MvcResult result =
        mvc.perform(get("/stream/a.c4gh").header(HttpHeaders.PROXY_AUTHORIZATION, bearer()))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .contains("attachment");
    assertThat(result.getResponse().getContentAsString()).isEqualTo("crypt4gh");
  }

  @Test
  void deletingAMissingFileIsFileNotFound() throws Exception {
    when(tsd.deleteFile(TSD_TOKEN, "ega", "a.c4gh")).thenReturn(status(new Message(), 404));

    assertProblem(
        mvc.perform(withCredentials(delete("/files").param("fileName", "a.c4gh"))),
        ErrorCode.FILE_NOT_FOUND);
    verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void chunkWithoutMd5IsInvalidRequestBeforeTouchingTsd() throws Exception {
    assertProblem(
        mvc.perform(withCredentials(patch("/stream/a.c4gh").content(new byte[] {1}))),
        ErrorCode.INVALID_REQUEST);
    verify(tsd, never()).initializeResumableUpload(any(), any(), any(), any());
  }

  @Test
  void invalidNotifyIsInvalidRequest() throws Exception {
    assertProblem(
        mvc.perform(
            withCredentials(
                post("/stream/a.c4gh/notify").param("fileSize", "10").param("sha256", "xyz"))),
        ErrorCode.INVALID_REQUEST);
    verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void springsOwnClientErrorsKeepTheirStatusAndGetACode() throws Exception {
    MvcResult result = mvc.perform(withCredentials(put("/files"))).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(405);
    assertThat(body(result)).containsEntry("code", "INVALID_REQUEST").containsKey("requestId");
  }

  @Test
  void checksumMismatchIsChecksumMismatchAndDiscardsTheUpload() throws Exception {
    Chunk chunk = status(new Chunk(), 201);
    chunk.setId("upload-1");
    when(tsd.initializeResumableUpload(eq(TSD_TOKEN), eq("ega"), any(), eq("a.c4gh")))
        .thenReturn(chunk);
    ResumableUpload upload = new ResumableUpload();
    upload.setId("upload-1");
    upload.setMd5Sum("0".repeat(32));
    when(tsd.getResumableUpload(TSD_TOKEN, "ega", "upload-1")).thenReturn(Optional.of(upload));

    assertProblem(
        mvc.perform(
            withCredentials(
                patch("/stream/a.c4gh").param("md5", "f".repeat(32)).content(new byte[] {1}))),
        ErrorCode.CHECKSUM_MISMATCH);
    verify(tsd).deleteResumableUpload(TSD_TOKEN, "ega", "upload-1");
  }

  @Test
  void egaAccountMappedToAnotherLsAaiAccountIsAccountMappingConflict() throws Exception {
    when(jdbcTemplate.queryForList(anyString(), eq(String.class), any()))
        .thenReturn(List.of("someone-else@lifescience-ri.eu"));

    assertProblem(
        mvc.perform(
            withCredentials(
                post("/stream/a.c4gh/notify").param("fileSize", "10").param("sha256", SHA256))),
        ErrorCode.ACCOUNT_MAPPING_CONFLICT);
  }

  @Test
  void unexpectedFailureIsInternal() throws Exception {
    when(tsd.listFiles(eq(TSD_TOKEN), anyString(), anyInt(), anyInt()))
        .thenThrow(new IllegalStateException("boom " + LEAK));

    assertProblem(mvc.perform(withCredentials(get("/files"))), ErrorCode.INTERNAL);
  }

  @Test
  void exportWithoutAdminCredentialsIsMissingCredentialsWithoutABasicChallenge() throws Exception {
    MvcResult result = assertProblem(mvc.perform(gdiExport()), ErrorCode.MISSING_CREDENTIALS);

    assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
  }

  @Test
  void exportWithWrongAdminCredentialsIsAdminAuthFailedWithoutABasicChallenge() throws Exception {
    MvcResult result =
        assertProblem(
            mvc.perform(
                gdiExport()
                    .header(HttpHeaders.AUTHORIZATION, basic("test-admin", "wrong-password"))),
            ErrorCode.ADMIN_AUTH_FAILED);

    assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
  }

  @Test
  void exportWithAForgedTokenIsTokenInvalidWithoutABearerChallenge() throws Exception {
    when(tokenService.parseVerified(LS_AAI_TOKEN)).thenThrow(tokenInvalid());

    MvcResult result =
        assertProblem(
            mvc.perform(gdiExport().header(HttpHeaders.AUTHORIZATION, adminBasic())),
            ErrorCode.ELIXIR_AAI_TOKEN_INVALID);

    assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
  }

  @Test
  void exportWithANonBasicAuthorizationIsMissingCredentials() throws Exception {
    assertProblem(
        mvc.perform(gdiExport().header(HttpHeaders.AUTHORIZATION, bearer())),
        ErrorCode.MISSING_CREDENTIALS);
  }

  /** A 401 on the proxy's own CEGA login says nothing about the user's password. */
  @Test
  void cegaRejectingTheProxyItselfIsCegaUnavailable() throws Exception {
    when(cega.getCredentials(EGA_USER))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                LEAK.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

    assertProblem(mvc.perform(withCredentials(get("/files"))), ErrorCode.CEGA_UNAVAILABLE);
  }

  @Test
  void exportWithoutAMatchingVisaIsVisaDenied() throws Exception {
    assertProblem(
        mvc.perform(gdiExport().header(HttpHeaders.AUTHORIZATION, adminBasic())),
        ErrorCode.VISA_DENIED);
    verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void exportWithTheBrokerDownIsUpstreamFailed() throws Exception {
    doThrow(new AmqpConnectException(new IOException("Connection refused " + LEAK)))
        .when(rabbitTemplate)
        .convertAndSend(
            anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));

    assertProblem(
        mvc.perform(
            post("/export/fega")
                .header(HttpHeaders.AUTHORIZATION, adminBasic())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"visaToken\":\"visa\",\"id\":\"EGAD00000000001\",\"userPublicKey\":\"key\",\"type\":\"DATASET_ID\"}")),
        ErrorCode.UPSTREAM_FAILED);
  }

  /** CEGA and TSD failures whose own text must never reach the client. */
  static Stream<Arguments> upstreamFailures() {
    return Stream.of(
        Arguments.of(
            "CEGA unknown user",
            ErrorCode.EGA_AUTH_FAILED,
            (ThrowingConsumer<ErrorContractTest>)
                t ->
                    when(t.cega.getCredentials(EGA_USER))
                        .thenThrow(
                            HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST,
                                LEAK,
                                HttpHeaders.EMPTY,
                                LEAK.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8))),
        Arguments.of(
            "CEGA 5xx",
            ErrorCode.CEGA_UNAVAILABLE,
            (ThrowingConsumer<ErrorContractTest>)
                t ->
                    when(t.cega.getCredentials(EGA_USER))
                        .thenThrow(
                            HttpServerErrorException.create(
                                HttpStatus.BAD_GATEWAY,
                                LEAK,
                                HttpHeaders.EMPTY,
                                LEAK.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8))),
        Arguments.of(
            "TSD I/O failure",
            ErrorCode.UPSTREAM_FAILED,
            (ThrowingConsumer<ErrorContractTest>)
                t ->
                    when(t.tsd.listFiles(eq(TSD_TOKEN), anyString(), anyInt(), anyInt()))
                        .thenThrow(new IOException(LEAK))),
        Arguments.of(
            "TSD error status",
            ErrorCode.UPSTREAM_FAILED,
            (ThrowingConsumer<ErrorContractTest>)
                t -> {
                  TSDFiles failed = listing(503, null);
                  failed.setStatusText(LEAK);
                  when(t.tsd.listFiles(eq(TSD_TOKEN), anyString(), anyInt(), anyInt()))
                      .thenReturn(failed);
                }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("upstreamFailures")
  void upstreamTextNeverReachesTheClient(
      String name, ErrorCode code, ThrowingConsumer<ErrorContractTest> failure) throws Throwable {
    failure.accept(this);

    MvcResult result = assertProblem(mvc.perform(withCredentials(get("/files"))), code);

    assertThat(result.getResponse().getContentAsString()).doesNotContain(LEAK);
  }

  /**
   * Asserts the status, the problem media type, the fixed detail, and that {@code code} and {@code
   * requestId} are top-level members, which is where clients read them.
   */
  private MvcResult assertProblem(ResultActions actions, ErrorCode code) throws Exception {
    MvcResult result = actions.andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(code.getStatus().value());
    assertThat(result.getResponse().getContentType())
        .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    Map<String, Object> problem = body(result);
    assertThat(PROBLEM_MEMBERS).containsAll(problem.keySet());
    assertThat(problem)
        .containsEntry("status", code.getStatus().value())
        .containsEntry("title", code.getTitle())
        .containsEntry("detail", code.getDetail())
        .containsEntry("code", code.name());
    assertThat((String) problem.get("requestId")).isNotBlank();
    assertThat(result.getResponse().getContentAsString()).doesNotContain(LEAK);
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> body(MvcResult result) throws Exception {
    return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
  }

  private static MockHttpServletRequestBuilder withCredentials(
      MockHttpServletRequestBuilder builder) {
    return builder
        .header(HttpHeaders.PROXY_AUTHORIZATION, bearer())
        .header(HttpHeaders.AUTHORIZATION, egaBasic());
  }

  private static MockHttpServletRequestBuilder gdiExport() {
    return post("/export/gdi")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"accessToken\":\""
                + LS_AAI_TOKEN
                + "\",\"id\":\"EGAD00000000001\",\"userPublicKey\":\"key\",\"type\":\"DATASET_ID\"}");
  }

  private static ProxyException tokenInvalid() {
    return new ProxyException(
        ErrorCode.ELIXIR_AAI_TOKEN_INVALID,
        "verification failed",
        new SignatureException("JWT signature does not match " + LEAK));
  }

  private static String bearer() {
    return "Bearer " + LS_AAI_TOKEN;
  }

  private static String egaBasic() {
    return basic(EGA_USER, EGA_PASSWORD);
  }

  private static String adminBasic() {
    return basic("test-admin", "test-admin-password");
  }

  private static String basic(String username, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private static Token token(int statusCode) {
    Token token = status(new Token(), statusCode);
    if (statusCode == 200) {
      token.setToken(TSD_TOKEN);
    }
    return token;
  }

  private static TSDFiles listing(int statusCode, List<TSDFile> files) {
    TSDFiles listing = status(new TSDFiles(), statusCode);
    listing.setFiles(files);
    return listing;
  }

  private static <T extends TSDFileAPIResponse> T status(T response, int statusCode) {
    response.setStatusCode(statusCode);
    return response;
  }
}
