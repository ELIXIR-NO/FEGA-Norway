package no.uio.ifi.tc;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.jsonwebtoken.JwtException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.net.ssl.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.tc.common.Masker;
import no.uio.ifi.tc.model.Environment;
import no.uio.ifi.tc.model.pojo.*;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

/** Main class of the library, encapsulating TSD File API client methods. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TSDFileAPIClient {

  private static final String BASE_URL = "%s://%s%s/%s/%s%s";
  private static final String BEARER = "Bearer ";
  private static final String USER_CLAIM = "user";
  private static final String ID_TOKEN = "idtoken";

  private final Gson gson = new Gson();

  private OkHttpClient httpClient;

  private String protocol;
  private String host;
  private Environment environment;
  private String version;
  private String project;
  private String accessKey;

  public TSDFileAPIClient(OkHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Lists uploaded files.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param page Page number (1-based).
   * @param perPage Items per page.
   * @return API response; the files are set only when the status is 2xx.
   * @throws IOException In case of I/O error, or a 2xx body that is not valid JSON.
   */
  public TSDFiles listFiles(String token, String appId, int page, int perPage) throws IOException {
    String url =
        getURL(getEndpoint(token, appId, "/files")) + "?page=" + page + "&per_page=" + perPage;
    log.info("TSDFileAPIClient making request: {}", Masker.maskEmailInUri(url));

    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).build();

    return execute(request, TSDFiles.class, TSDFiles::new);
  }

  /**
   * Streams the input at once, not chunked.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param inputStream Stream to send to TSD.
   * @param fileName File name to use.
   * @return API response.
   * @throws IOException In case of I/O related errors.
   */
  public Message uploadFile(String token, String appId, InputStream inputStream, String fileName)
      throws IOException {
    String url = getURL(getEndpoint(token, appId, "/files/" + fileName));

    // Read all bytes from inputStream
    byte[] data = inputStream.readAllBytes();

    RequestBody requestBody = RequestBody.create(data, MediaType.parse("application/octet-stream"));
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", BEARER + token)
            .put(requestBody)
            .build();

    return execute(request, Message.class, Message::new);
  }

  /**
   * Deletes uploaded file.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param fileName File name to use.
   * @return API response.
   */
  public Message deleteFile(String token, String appId, String fileName) throws IOException {
    String url = getURL(getEndpoint(token, appId, "/files/" + fileName));

    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).delete().build();

    return execute(request, Message.class, Message::new);
  }

  /**
   * Lists all initiated and not yet finished resumable uploads by file and Upload ID.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @return API response.
   */
  public ResumableUploads listResumableUploads(String token, String appId) throws IOException {
    String url = getURL(getEndpoint(token, appId, "/resumables"));

    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).get().build();

    return execute(request, ResumableUploads.class, ResumableUploads::new);
  }

  /**
   * Lists all initiated and not yet finished resumable uploads by file and Upload ID.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param uploadId Resumable upload ID.
   * @return The upload, or empty when TSD lists no upload with that ID.
   * @throws IOException In case of I/O error, or when TSD fails to list the resumable uploads.
   */
  public Optional<ResumableUpload> getResumableUpload(String token, String appId, String uploadId)
      throws IOException {
    ResumableUploads resumableUploads = listResumableUploads(token, appId);
    if (!isSuccessful(resumableUploads) || resumableUploads.getResumables() == null) {
      throw new IOException(
          "TSD failed to list resumable uploads: HTTP " + resumableUploads.getStatusCode());
    }
    Optional<ResumableUpload> resumableUpload =
        resumableUploads.getResumables().stream()
            .filter(u -> u.getId().equalsIgnoreCase(uploadId))
            .findAny();
    resumableUpload.ifPresent(r -> r.setStatusCode(resumableUploads.getStatusCode()));
    resumableUpload.ifPresent(r -> r.setStatusText(resumableUploads.getStatusText()));
    return resumableUpload;
  }

  /**
   * Uploads the first chunk of data (initializes resumable upload).
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param firstChunk First chunk of data.
   * @param fileName File name to use.
   * @return API response.
   */
  public Chunk initializeResumableUpload(
      String token, String appId, byte[] firstChunk, String fileName) throws IOException {
    String url = getURL(getEndpoint(token, appId, "/files/" + fileName + "?chunk=1"));

    RequestBody requestBody =
        RequestBody.create(firstChunk, MediaType.parse("application/octet-stream"));
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", BEARER + token)
            .patch(requestBody)
            .build();

    return execute(request, Chunk.class, Chunk::new);
  }

  /**
   * Upload another chunk of data (NB: chunks must arrive in order).
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param chunkNumber Order number of the chunk.
   * @param chunk Chunk of data to upload.
   * @param uploadId Upload ID.
   * @return API response.
   */
  public Chunk uploadChunk(
      String token, String appId, long chunkNumber, byte[] chunk, String uploadId)
      throws IOException {
    ResumableUpload resumableUpload = getResumableUpload(token, appId, uploadId).orElseThrow();
    String url =
        getURL(
            getEndpoint(
                token,
                appId,
                "/files/"
                    + resumableUpload.getFileName()
                    + "?chunk="
                    + chunkNumber
                    + "&id="
                    + uploadId));

    RequestBody requestBody =
        RequestBody.create(chunk, MediaType.parse("application/octet-stream"));
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", BEARER + token)
            .patch(requestBody)
            .build();

    return execute(request, Chunk.class, Chunk::new);
  }

  /**
   * Finalizes resumable upload.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param uploadId Upload ID.
   * @return API response.
   */
  public Chunk finalizeResumableUpload(String token, String appId, String uploadId)
      throws IOException {
    ResumableUpload resumableUpload = getResumableUpload(token, appId, uploadId).orElseThrow();
    String url =
        getURL(
            getEndpoint(
                token,
                appId,
                "/files/" + resumableUpload.getFileName() + "?chunk=end&id=" + uploadId));

    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", BEARER + token)
            .patch(RequestBody.create(new byte[0], null)) // Empty body for PATCH request
            .build();

    return execute(request, Chunk.class, Chunk::new);
  }

  /**
   * Deletes initiated and not yet finished resumable upload.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param uploadId Upload ID.
   * @return API response.
   */
  public Message deleteResumableUpload(String token, String appId, String uploadId)
      throws IOException {
    ResumableUpload resumableUpload = getResumableUpload(token, appId, uploadId).orElseThrow();
    String url =
        getURL(
            getEndpoint(
                token, appId, "/resumables/" + resumableUpload.getFileName() + "?id=" + uploadId));

    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).delete().build();

    return execute(request, Message.class, Message::new);
  }

  /**
   * Downloads file by its name.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param fileName File name to download.
   * @param outputStream OutputStream to write file to.
   * @return API response.
   */
  public TSDFileAPIResponse downloadFile(
      String token, String appId, String fileName, OutputStream outputStream) throws IOException {
    OkHttpClient client = this.httpClient;
    String url = getURL(getEndpoint(token, appId, "/files/" + fileName));

    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).build();

    TSDFileAPIResponse tsdFileAPIResponse = new TSDFileAPIResponse();

    try (Response response = client.newCall(request).execute()) {
      tsdFileAPIResponse.setStatusCode(response.code());
      tsdFileAPIResponse.setStatusText(response.message());

      if (response.isSuccessful() && response.body() != null) {
        try (InputStream responseStream = response.body().byteStream()) {
          IOUtils.copy(responseStream, outputStream);
        }
      }
    }

    return tsdFileAPIResponse;
  }

  /**
   * Retrieves the auth token by using non-TSD identity (OIDC provided).
   *
   * @param tokenType Type of the token to request.
   * @param oidcProvider OIDC provider name.
   * @param idToken ID token obtained from the OIDC provider.
   * @return API response; the token is set only when the status is 2xx.
   * @throws IOException In case of I/O error, or a 2xx body that is not valid JSON.
   */
  public Token getToken(String tokenType, String oidcProvider, String idToken) throws IOException {
    String url =
        getURL(
            String.format("/auth/%s/token?type=", oidcProvider.toLowerCase())
                + tokenType.toLowerCase());
    log.info(url);
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body =
        RequestBody.create(String.format("{\"%s\":\"%s\"}", ID_TOKEN, idToken), JSON);
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Authorization", BEARER + accessKey)
            .post(body)
            .build();

    return execute(request, Token.class, Token::new);
  }

  /**
   * Sends the request and reads TSD's status before the body, which is parsed only on 2xx, so a
   * failed call comes back as its status with no payload rather than as a half-filled object.
   */
  private <T extends TSDFileAPIResponse> T execute(
      Request request, Class<T> type, Supplier<T> empty) throws IOException {
    try (Response response = httpClient.newCall(request).execute()) {
      T result = null;
      if (response.isSuccessful()) {
        try {
          result = gson.fromJson(response.body().string(), type);
        } catch (JsonParseException e) {
          throw new IOException(
              "TSD answered HTTP " + response.code() + " with a body that is not valid JSON", e);
        }
      }
      if (result == null) {
        result = empty.get();
      }
      result.setStatusCode(response.code());
      result.setStatusText(response.message());
      return result;
    }
  }

  private static boolean isSuccessful(TSDFileAPIResponse response) {
    return response.getStatusCode() >= 200 && response.getStatusCode() < 300;
  }

  private String getEndpoint(String token, String appId, String path) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        throw new IllegalArgumentException("Invalid JWT format");
      }
      String payloadJson =
          new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      Map<String, Object> payload = new Gson().fromJson(payloadJson, Map.class);
      String userClaim = (String) payload.get(USER_CLAIM);
      return String.format("/%s/%s%s", appId, userClaim, path);
    } catch (Exception e) {
      log.error("Error decoding JWT token: {}", e.getMessage());
      throw new JwtException("Invalid JWT token", e);
    }
  }

  private String getURL(String endpoint) {
    return String.format(
        BASE_URL, protocol, environment.getEnvironment(), host, version, project, endpoint);
  }

  /** Class that build the TSDFileAPIClient instance. */
  public static class Builder {

    private static final String DEFAULT_HOST = "api.tsd.usit.no";
    private static final Environment DEFAULT_ENVIRONMENT = Environment.PRODUCTION;
    private static final String DEFAULT_VERSION = "v1";
    private static final String DEFAULT_PROJECT = "p11";
    private OkHttpClient OkhttpClient;
    private String clientCertificateStore;
    private String clientCertificateStorePassword;
    private Boolean secure;
    private Boolean checkCertificate;
    private String host;
    private Environment environment;
    private String version;
    private String project;
    private String accessKey;

    /** Public parameter-less constructor. */
    public Builder() {}

    /**
     * Sets custom HTTP client using OkHttp.
     *
     * @param OkhttpClient OkHttp client.
     * @return Builder instance.
     */
    public Builder httpClient(OkHttpClient OkhttpClient) {
      this.OkhttpClient = OkhttpClient;
      return this;
    }

    /**
     * Sets client certificate store location (PKCS#12) along with its password.
     *
     * @param clientCertificateStore Client certificate store location.
     * @param clientCertificateStorePassword client certificate store password.
     * @return Builder instance.
     */
    public Builder clientCertificateStore(
        String clientCertificateStore, String clientCertificateStorePassword) {
      this.clientCertificateStore = clientCertificateStore;
      this.clientCertificateStorePassword = clientCertificateStorePassword;
      return this;
    }

    /**
     * Defines whether you use HTTP or HTTPS.
     *
     * @param secure <code>true</code> for HTTPS, <code>false</code> otherwise.
     * @return Builder instance.
     */
    public Builder secure(boolean secure) {
      this.secure = secure;
      return this;
    }

    /**
     * Defines whether certificate will be checked in case of HTTPS connection.
     *
     * @param checkCertificate <code>true</code> for checking, <code>false</code> otherwise.
     * @return Builder instance.
     */
    public Builder checkCertificate(boolean checkCertificate) {
      this.checkCertificate = checkCertificate;
      return this;
    }

    public Builder secure(String secure) {
      this.secure = Boolean.parseBoolean(secure);
      return this;
    }

    /**
     * Sets hostname (maybe with port).
     *
     * @param host Hostname (optionally with a port) to work against.
     * @return Builder instance.
     */
    public Builder host(String host) {
      this.host = host;
      return this;
    }

    /**
     * Sets the environment.
     *
     * @param environment Environment to use.
     * @return Builder instance.
     */
    public Builder environment(String environment) {
      this.environment = Environment.get(environment);
      return this;
    }

    /**
     * Sets the version to use.
     *
     * @param version Version of the TSD File API.
     * @return Builder instance.
     */
    public Builder version(String version) {
      this.version = version;
      return this;
    }

    /**
     * Sets the project to use.
     *
     * @param project Project ID in the TSD.
     * @return Builder instance.
     */
    public Builder project(String project) {
      this.project = project;
      return this;
    }

    /**
     * Sets the access key to use.
     *
     * @param accessKey TSD File API access key for Basic Auth.
     * @return Builder instance.
     */
    public Builder accessKey(String accessKey) {
      this.accessKey = accessKey;
      return this;
    }

    /**
     * Build the client.
     *
     * @return Client.
     */
    public TSDFileAPIClient build() {
      OkHttpClient httpClient;

      boolean enableSsl =
          Boolean.parseBoolean(Optional.ofNullable(System.getenv("TSD_SECURE")).orElse("true"));

      if (this.OkhttpClient != null) {
        httpClient = this.OkhttpClient;
      } else {
        OkHttpClient.Builder httpClientBuilder =
            new OkHttpClient.Builder()
                // Set connection, read, and write timeouts for the HTTP client
                .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS);

        // SSL Verification and Client Certificate Handling
        if (enableSsl
            && StringUtils.isNotEmpty(clientCertificateStore)
            && StringUtils.isNotEmpty(clientCertificateStorePassword)) {
          try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream certInputStream = new FileInputStream(clientCertificateStore)) {
              keyStore.load(certInputStream, clientCertificateStorePassword.toCharArray());
            }

            KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, clientCertificateStorePassword.toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

            final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            final X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

            httpClientBuilder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);

            // Optionally disable SSL verification if needed
            if (this.checkCertificate != null && !this.checkCertificate) {
              httpClientBuilder.hostnameVerifier((hostname, session) -> true);
            }
          } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL context", e);
          }
        }

        httpClient = httpClientBuilder.build();
      }

      TSDFileAPIClient tsdFileAPIClient = new TSDFileAPIClient(httpClient);

      tsdFileAPIClient.protocol = enableSsl ? "https" : "http";
      tsdFileAPIClient.host = this.host == null ? DEFAULT_HOST : this.host;
      tsdFileAPIClient.environment =
          this.environment == null ? DEFAULT_ENVIRONMENT : this.environment;
      tsdFileAPIClient.version = this.version == null ? DEFAULT_VERSION : this.version;
      tsdFileAPIClient.project = this.project == null ? DEFAULT_PROJECT : this.project;

      if (StringUtils.isEmpty(this.accessKey)) {
        throw new IllegalArgumentException("Access key must be set");
      }
      tsdFileAPIClient.accessKey = this.accessKey;

      return tsdFileAPIClient;
    }
  }
}
