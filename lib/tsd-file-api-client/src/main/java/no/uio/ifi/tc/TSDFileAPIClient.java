package no.uio.ifi.tc;

import com.auth0.jwt.JWT;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.tc.model.Environment;
import no.uio.ifi.tc.model.pojo.*;
import okhttp3.*;
import org.apache.commons.io.IOUtils;

/** Main class of the library, encapsulating TSD File API client methods. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TSDFileAPIClient {

  private static final String BASE_URL = "%s://%s%s/%s/%s%s";
  private static final String BEARER = "Bearer ";
  private static final String USER_CLAIM = "user";
  private static final String ID_TOKEN = "idtoken";

  private final Gson gson = new Gson();

  private String protocol;
  private String host;
  private Environment environment;
  private String version;
  private String project;
  private String accessKey;

  public TSDFileAPIClient(OkHttpClient httpClient) {}

  /**
   * Lists uploaded files.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @return API response.
   */
  public TSDFiles listFiles(String token, String appId) {
    OkHttpClient client = new OkHttpClient();
    String url = getURL(getEndpoint(token, appId, "/files"));

    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).build();

    TSDFiles tsdFiles = new TSDFiles();

    try {
      Response response = client.newCall(request).execute();

      // TODO: Ensure the response body is not null
      if (response.body() != null) {
        String responseBody = response.body().string();
        tsdFiles = gson.fromJson(responseBody, TSDFiles.class);
      }

      tsdFiles.setStatusCode(response.code());
      tsdFiles.setStatusText(response.message());
    } catch (IOException | JsonSyntaxException e) {
      log.error(e.getMessage(), e);
    }

    return tsdFiles;
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
    OkHttpClient client = new OkHttpClient();
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

    Message message = new Message();

    try (Response response = client.newCall(request).execute()) {
      // Ensure the response body is not null
      if (response.body() != null) {
        String responseBody = response.body().string();
        message = gson.fromJson(responseBody, Message.class);
      }

      message.setStatusCode(response.code());
      message.setStatusText(response.message());
    } catch (JsonSyntaxException e) {
      log.error(e.getMessage(), e);
    }

    return message;
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

    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).delete().build();

    Message message = new Message();

    try (Response response = client.newCall(request).execute()) {
      message.setStatusCode(response.code());
      message.setStatusText(response.message());

      if (response.isSuccessful() && response.body() != null) {
        String responseBody = response.body().string();
        message = gson.fromJson(responseBody, Message.class);
      }
    } catch (JsonSyntaxException e) {
      log.error(e.getMessage(), e);
    }

    return message;
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

    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).get().build();

    ResumableUploads resumableUploads = new ResumableUploads();

    try (Response response = client.newCall(request).execute()) {
      resumableUploads.setStatusCode(response.code());
      resumableUploads.setStatusText(response.message());

      if (response.isSuccessful() && response.body() != null) {
        String responseBody = response.body().string();
        resumableUploads = gson.fromJson(responseBody, ResumableUploads.class);
      }
    } catch (JsonSyntaxException e) {
      log.error(e.getMessage(), e);
    }

    return resumableUploads;
  }

  /**
   * Lists all initiated and not yet finished resumable uploads by file and Upload ID.
   *
   * @param token Auth token to use.
   * @param appId TSD application ID.
   * @param uploadId Resumable upload ID.
   * @return API response.
   */
  public Optional<ResumableUpload> getResumableUpload(String token, String appId, String uploadId)
      throws IOException {
    ResumableUploads resumableUploads = listResumableUploads(token, appId);
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

    OkHttpClient client = new OkHttpClient();
    RequestBody requestBody =
        RequestBody.create(firstChunk, MediaType.parse("application/octet-stream"));
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", BEARER + token)
            .patch(requestBody)
            .build();

    Chunk chunkResponse = new Chunk();

    try (Response response = client.newCall(request).execute()) {
      chunkResponse.setStatusCode(response.code());
      chunkResponse.setStatusText(response.message());
      String _body = Objects.requireNonNull(response.body()).string();
      if (response.isSuccessful() && _body != null) {
        chunkResponse = gson.fromJson(_body, Chunk.class);
      }
    } catch (JsonSyntaxException e) {
      log.error(e.getMessage(), e);
    }

    return chunkResponse;
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

    OkHttpClient client = new OkHttpClient();
    RequestBody requestBody =
        RequestBody.create(chunk, MediaType.parse("application/octet-stream"));
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", BEARER + token)
            .patch(requestBody)
            .build();

    Chunk chunkResponse = new Chunk();

    try (Response response = client.newCall(request).execute()) {
      chunkResponse.setStatusCode(response.code());
      chunkResponse.setStatusText(response.message());

      if (response.isSuccessful() && response.body() != null) {
        String responseBody = response.body().string();
        chunkResponse = gson.fromJson(responseBody, Chunk.class);
      }
    } catch (JsonSyntaxException e) {
      log.error(e.getMessage(), e);
    }

    return chunkResponse;
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

    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", BEARER + token)
            .patch(RequestBody.create(new byte[0], null)) // Empty body for PATCH request
            .build();

    Chunk chunkResponse = new Chunk();

    try (Response response = client.newCall(request).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        String responseBody = response.body().string();
        chunkResponse = gson.fromJson(responseBody, Chunk.class);
      }
      chunkResponse.setStatusCode(response.code());
      chunkResponse.setStatusText(response.message());
    } catch (JsonSyntaxException e) {
      log.error(e.getMessage(), e);
    }

    return chunkResponse;
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

    OkHttpClient client = new OkHttpClient();
    Request request =
        new Request.Builder().url(url).addHeader("Authorization", BEARER + token).delete().build();

    Message message = new Message();

    try (Response response = client.newCall(request).execute()) {
      message.setStatusCode(response.code());
      message.setStatusText(response.message());

      if (response.isSuccessful() && response.body() != null) {
        String responseBody = response.body().string();
        message = gson.fromJson(responseBody, Message.class);
      }
    } catch (JsonSyntaxException e) {
      log.error(e.getMessage(), e);
    }

    return message;
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
    OkHttpClient client = new OkHttpClient();
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
   * @return API response.
   */
  public Token getToken(String tokenType, String oidcProvider, String idToken) throws IOException {
    String url =
        getURL(
            String.format("/auth/%s/token?type=", oidcProvider.toLowerCase())
                + tokenType.toLowerCase());
    log.info(url);
    OkHttpClient client = new OkHttpClient();
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

    Token token = new Token();
    Response response;

    try {
      response = client.newCall(request).execute();
      token.setStatusCode(response.code());
      token.setStatusText(response.message());
      ResponseBody responseBody = response.body();
      if (response.isSuccessful() && responseBody != null) {
        String bodyString = responseBody.string();
        token.setToken(
            JsonParser.parseString(bodyString).getAsJsonObject().get("token").getAsString());
      }
      response.close();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }

    return token;
  }

  private String getEndpoint(String token, String appId, String path) {
    return String.format(
        "/%s/%s%s", appId, JWT.decode(token).getClaim(USER_CLAIM).asString(), path);
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
     * Defines whether use HTTP or HTTPS.
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
      // TODO: Implement SSL client authentication in OkHttpClient
      if (this.OkhttpClient != null) {
        httpClient = this.OkhttpClient;
      } else {
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        httpClient = httpClientBuilder.build();
      }

      TSDFileAPIClient tsdFileAPIClient = new TSDFileAPIClient(httpClient);

      tsdFileAPIClient.protocol = "http";
      tsdFileAPIClient.host = this.host == null ? DEFAULT_HOST : this.host;
      tsdFileAPIClient.environment =
          this.environment == null ? DEFAULT_ENVIRONMENT : this.environment;
      tsdFileAPIClient.version = this.version == null ? DEFAULT_VERSION : this.version;
      tsdFileAPIClient.project = this.project == null ? DEFAULT_PROJECT : this.project;
      tsdFileAPIClient.accessKey = this.accessKey;

      return tsdFileAPIClient;
    }
  }
}
