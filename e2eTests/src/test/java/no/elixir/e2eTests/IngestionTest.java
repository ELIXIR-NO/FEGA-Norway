package no.elixir.e2eTests;

import static no.elixir.e2eTests.utils.JsonUtils.toCompactJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.auth0.jwt.algorithms.Algorithm;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import no.elixir.crypt4gh.stream.Crypt4GHInputStream;
import no.elixir.crypt4gh.stream.Crypt4GHOutputStream;
import no.elixir.crypt4gh.util.KeyUtils;
import no.elixir.e2eTests.config.Environment;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.utils.CertificateUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IngestionTest {

  private static final Environment env = new Environment();
  private static final Logger log = LoggerFactory.getLogger(IngestionTest.class);
  private static final KeyUtils keyUtils = KeyUtils.getInstance();

  private File rawFile;
  private File encFile;
  private String rawSHA256Checksum;
  private String encSHA256Checksum;
  private String rawMD5Checksum;
  private String stableId;
  private String datasetId;
  private String archivePath;
  private String correlationId;

  @BeforeEach
  public void setup() throws Exception {

    String basePath = "./";

    long fileSize = 1024 * 1024 * 10;
    log.info("Generating {} bytes file to submit...", fileSize);

    rawFile = no.elixir.e2eTests.utils.FileUtils.createRandomFile(basePath, fileSize);

    byte[] bytes = DigestUtils.sha256(Files.newInputStream(rawFile.toPath()));
    rawSHA256Checksum = Hex.encodeHexString(bytes);
    log.info("Raw SHA256 checksum: {}", rawSHA256Checksum);

    byte[] bytes2 = DigestUtils.md5(Files.newInputStream(rawFile.toPath()));
    rawMD5Checksum = Hex.encodeHexString(bytes2);
    log.info("Raw MD5 checksum: {}", rawMD5Checksum);

    log.info("Generating sender and recipient key-pairs...");
    KeyPair senderKeyPair = keyUtils.generateKeyPair();

    log.info("Encrypting the file with Crypt4GH...");
    encFile = new File(basePath + rawFile.getName() + ".enc");

    PublicKey localEGAInstancePublicKey = keyUtils.readPublicKey(getCertificateFile("ega.pub.pem"));

    try (FileOutputStream fileOutputStream = new FileOutputStream(encFile);
        Crypt4GHOutputStream crypt4GHOutputStream =
            new Crypt4GHOutputStream(
                fileOutputStream, senderKeyPair.getPrivate(), localEGAInstancePublicKey)) {
      FileUtils.copyFile(rawFile, crypt4GHOutputStream);
    }

    bytes = DigestUtils.sha256(Files.newInputStream(encFile.toPath()));
    encSHA256Checksum = Hex.encodeHexString(bytes);
    log.info("Enc SHA256 checksum: {}", encSHA256Checksum);

    try (UnirestInstance instance = Unirest.primaryInstance()) {
      instance.config().verifySsl(false).hostnameVerifier(NoopHostnameVerifier.INSTANCE);
    }
  }

  @Test
  public void performEndToEndTest() throws Exception {
    upload();
    // Wait for triggers to be set up at CEGA.
    // Not really needed if using local CEGA container.
    Thread.sleep(5000);
    triggerIngestMessageFromCEGA();
    Thread.sleep(5000); // Wait for the LEGA ingest and verify services to complete and update DB
    triggerAccessionMessageFromCEGA();
    Thread.sleep(5000); // Wait for LEGA finalize service to complete and update DB
    // Verify that everything is ok so far
    verifyAfterFinalizeAndLookUpAccessionID();
    // Trigger the process further,
    // with retrieved information from earlier steps
    triggerMappingMessageFromCEGA();
    Thread.sleep(1000); // Wait for LEGA mapper service to store mapping
    triggerReleaseMessageFromCEGA();
    Thread.sleep(1000); // Wait for LEGA mapper service to update dataset status
    // Test and check that what we get out match the original inserted data at the top
    downloadDatasetAndVerifyResults();
  }

  private void upload() throws Exception {
    log.info("Uploading a file through a proxy...");
    String token = generateVisaToken("upload");
    log.info("Visa JWT token when uploading: {}", token);
    String md5Hex = DigestUtils.md5Hex(Files.newInputStream(encFile.toPath()));
    log.info("Encrypted MD5 checksum: {}", md5Hex);
    log.info("EGA_BOX_USERNAME: {}", env.getEga_box_username());
    String uploadURL =
        String.format(
            "https://%s:%s/stream/%s?md5=%s",
            env.getProxy_host(), env.getProxy_port(), encFile.getName(), md5Hex);
    JsonNode jsonResponse =
        Unirest.patch(uploadURL)
            .socketTimeout(1000000000)
            .basicAuth(env.getEga_box_username(), env.getEga_box_password())
            .header("Proxy-Authorization", "Bearer " + token)
            .body(FileUtils.readFileToByteArray(encFile))
            .asJson()
            .getBody();
    String uploadId = jsonResponse.getObject().getString("id");
    log.info("Upload ID: {}", uploadId);
    String finalizeURL =
        String.format(
            "https://%s:%s/stream/%s?uploadId=%s&chunk=end&sha256=%s&fileSize=%s",
            env.getProxy_host(),
            env.getProxy_port(),
            encFile.getName(),
            uploadId,
            encSHA256Checksum,
            FileUtils.sizeOf(encFile));
    HttpResponse<JsonNode> res =
        Unirest.patch(finalizeURL)
            .socketTimeout(1000000)
            .basicAuth(env.getEga_box_username(), env.getEga_box_password())
            .header("Proxy-Authorization", "Bearer " + token)
            .asJson();
    jsonResponse = res.getBody();
    assertEquals(201, jsonResponse.getObject().get("statusCode"));
  }

  private void triggerIngestMessageFromCEGA()
      throws Exception,
          TimeoutException,
          NoSuchAlgorithmException,
          KeyManagementException,
          URISyntaxException {
    log.info("Publishing ingestion message to CentralEGA...");
    ConnectionFactory factory = new ConnectionFactory();
    factory.useSslProtocol(createSslContext());
    factory.setUri(env.getBrokerConnectionString());
    Connection connectionFactory = factory.newConnection();
    Channel channel = connectionFactory.createChannel();
    correlationId = UUID.randomUUID().toString();

    AMQP.BasicProperties properties =
        new AMQP.BasicProperties()
            .builder()
            .deliveryMode(2)
            .contentType("application/json")
            .contentEncoding(StandardCharsets.UTF_8.displayName())
            .correlationId(correlationId)
            .build();

    String message = Strings.INGEST_MESSAGE.formatted(env.getEga_box_username(), encFile.getName());
    log.info(message);
    channel.basicPublish("localega", "files", properties, message.getBytes());

    channel.close();
    connectionFactory.close();
  }

  private void triggerAccessionMessageFromCEGA() throws Exception {
    log.info("Publishing accession message on behalf of CEGA to CEGA RMQ...");
    ConnectionFactory factory = new ConnectionFactory();
    factory.useSslProtocol(createSslContext());
    factory.setUri(env.getBrokerConnectionString());
    Connection connectionFactory = factory.newConnection();
    Channel channel = connectionFactory.createChannel();
    AMQP.BasicProperties properties =
        new AMQP.BasicProperties()
            .builder()
            .deliveryMode(2)
            .contentType("application/json")
            .contentEncoding(StandardCharsets.UTF_8.displayName())
            .correlationId(correlationId)
            .build();
    String randomFileAccessionID = "EGAF5" + getRandomNumber(10);
    String message =
        String.format(
            Strings.ACCESSION_MESSAGE,
            env.getEga_box_username(),
            encFile.getName(),
            randomFileAccessionID,
            rawSHA256Checksum,
            rawMD5Checksum);
    log.info(message);
    channel.basicPublish("localega", "files", properties, message.getBytes());
    channel.close();
    connectionFactory.close();
  }

  @SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
  private void verifyAfterFinalizeAndLookUpAccessionID() throws Exception {
    log.info("Starting verification of state after finalize step...");
    File rootCA = getCertificateFile("rootCA.pem");
    File client = getCertificateFile("client.pem");
    File clientKey = getCertificateFile("client-key.der");
    String url =
        String.format(
            "jdbc:postgresql://%s:%s/%s",
            env.getSda_db_host(), env.getSda_db_port(), env.getSda_db_database_name());
    Properties props = new Properties();
    props.setProperty("user", env.getSda_db_username());
    props.setProperty("password", env.getSda_db_password());
    props.setProperty("application_name", "LocalEGA");
    props.setProperty("sslmode", "verify-full");
    props.setProperty("sslcert", client.getAbsolutePath());
    //        props.setProperty("sslcert", "tmp/client.pem"/*client.getAbsolutePath()*/);
    props.setProperty("sslkey", clientKey.getAbsolutePath());
    //        props.setProperty("sslkey", "tmp/client-key.pem" /*clientKey.getAbsolutePath()*/);
    props.setProperty("sslrootcert", rootCA.getAbsolutePath());
    //        props.setProperty("sslrootcert", "tmp/rootCA.pem" /*rootCA.getAbsolutePath()*/);
    java.sql.Connection conn = DriverManager.getConnection(url, props);
    String sql =
        "select archive_path,stable_id from local_ega.files where status = 'READY' AND inbox_path = ?";
    PreparedStatement statement = conn.prepareStatement(sql);
    statement.setString(1, "/p11-dummy@elixir-europe.org/files/" + encFile.getName());
    ResultSet resultSet = statement.executeQuery();
    if (resultSet.wasNull() || !resultSet.next()) {
      fail("Verification failed");
    }
    archivePath = resultSet.getString(1);
    stableId = resultSet.getString(2);
    log.info("Stable ID: {}", stableId);
    log.info("Archive path: {}", archivePath);
    log.info("Verification completed successfully");
  }

  private void triggerMappingMessageFromCEGA() throws Exception {
    log.info("Mapping file to a dataset...");
    datasetId = "EGAD" + getRandomNumber(11);
    ConnectionFactory factory = new ConnectionFactory();
    factory.useSslProtocol(createSslContext());
    factory.setUri(env.getBrokerConnectionString());
    Connection connectionFactory = factory.newConnection();
    Channel channel = connectionFactory.createChannel();
    AMQP.BasicProperties properties =
        new AMQP.BasicProperties()
            .builder()
            .deliveryMode(2)
            .contentType("application/json")
            .contentEncoding(StandardCharsets.UTF_8.displayName())
            .correlationId(correlationId)
            .build();
    String message = String.format(Strings.MAPPING_MESSAGE, stableId, datasetId);
    log.info(message);
    channel.basicPublish("localega", "files", properties, message.getBytes());
    channel.close();
    connectionFactory.close();
    log.info("Mapping file to dataset ID message sent successfully");
  }

  private void triggerReleaseMessageFromCEGA()
      throws Exception, KeyManagementException, URISyntaxException, IOException, TimeoutException {
    log.info("Releasing the dataset...");
    ConnectionFactory factory = new ConnectionFactory();
    factory.useSslProtocol(createSslContext());
    factory.setUri(env.getBrokerConnectionString());
    Connection connectionFactory = factory.newConnection();
    Channel channel = connectionFactory.createChannel();
    AMQP.BasicProperties properties =
        new AMQP.BasicProperties()
            .builder()
            .deliveryMode(2)
            .contentType("application/json")
            .contentEncoding(StandardCharsets.UTF_8.displayName())
            .correlationId(correlationId)
            .build();
    String message = String.format(Strings.RELEASE_MESSAGE, datasetId);
    log.info(message);
    channel.basicPublish("localega", "files", properties, message.getBytes());
    channel.close();
    connectionFactory.close();
    log.info("Dataset release message sent successfully");
  }

  private void downloadDatasetAndVerifyResults() throws Exception {
    String token = generateVisaToken(datasetId);
    log.info("Visa JWT token when downloading: {}", token);
    String datasets =
        Unirest.get(
                String.format(
                    "https://%s:%s/metadata/datasets",
                    env.getSda_doa_host(), env.getSda_doa_port()))
            .header("Authorization", "Bearer " + token)
            .asString()
            .getBody();
    assertEquals(String.format("[\"%s\"]", datasetId).strip(), datasets.strip());
    // Meta data check
    String expected =
        toCompactJson(
            String.format(
                    Strings.EXPECTED_DOWNLOAD_METADATA,
                    stableId,
                    datasetId,
                    encFile.getName(),
                    archivePath,
                    rawSHA256Checksum)
                .strip());
    String actual =
        toCompactJson(
            Unirest.get(
                    String.format(
                        "https://%s:%s/metadata/datasets/%s/files",
                        env.getSda_doa_host(), env.getSda_doa_port(), datasetId))
                .header("Authorization", "Bearer " + token)
                .asString()
                .getBody()
                .strip());
    log.info("Expected: {}", expected);
    log.info("Actual: {}", actual);
    JSONAssert.assertEquals(expected, actual, false);
    // Fetch the non-encrypted file
    HttpResponse<byte[]> response =
        Unirest.get(
                String.format(
                    "https://%s:%s/files/%s",
                    env.getSda_doa_host(), env.getSda_doa_port(), stableId))
            .header("Authorization", "Bearer " + token)
            .asBytes();
    if (response.getStatus() == 200) { // Check if the response is OK
      byte[] file = response.getBody();
      String obtainedChecksum = Hex.encodeHexString(DigestUtils.sha256(file));
      assertEquals(rawSHA256Checksum, obtainedChecksum);
    } else {
      fail("Failed to fetch the file. Status: " + response.getStatus());
    }
    // Fetch the encrypted file
    KeyPair recipientKeyPair = keyUtils.generateKeyPair();
    StringWriter stringWriter = new StringWriter();
    keyUtils.writeCrypt4GHKey(stringWriter, recipientKeyPair.getPublic(), null);
    String key = stringWriter.toString();
    HttpResponse<byte[]> encFileRes =
        Unirest.get(
                String.format(
                    "https://%s:%s/files/%s?destinationFormat=CRYPT4GH",
                    env.getSda_doa_host(), env.getSda_doa_port(), stableId))
            .header("Authorization", "Bearer " + token)
            .header("Public-Key", key)
            .asBytes();
    if (encFileRes.getStatus() == 200) { // Check if the response is OK
      byte[] file = encFileRes.getBody();
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(file);
          Crypt4GHInputStream crypt4GHInputStream =
              new Crypt4GHInputStream(byteArrayInputStream, recipientKeyPair.getPrivate())) {
        IOUtils.copyLarge(crypt4GHInputStream, byteArrayOutputStream);
      }
      String obtainedChecksum =
          Hex.encodeHexString(DigestUtils.sha256(byteArrayOutputStream.toByteArray()));
      assertEquals(rawSHA256Checksum, obtainedChecksum);
    } else {
      fail("Failed to fetch the encrypted file. Status: " + response.getStatus());
    }
  }

  private String generateVisaToken(String resource) throws Exception {
    RSAPublicKey publicKey = getPublicKey();
    RSAPrivateKey privateKey = getPrivateKey();
    byte[] visaHeader = Base64.getUrlEncoder().encode(Strings.VISA_HEADER.getBytes());
    byte[] visaPayload =
        Base64.getUrlEncoder().encode(String.format(Strings.VISA_PAYLOAD, resource).getBytes());
    byte[] visaSignature = Algorithm.RSA256(publicKey, privateKey).sign(visaHeader, visaPayload);
    return "%s.%s.%s"
        .formatted(
            new String(visaHeader),
            new String(visaPayload),
            Base64.getUrlEncoder().encodeToString(visaSignature));
  }

  private RSAPublicKey getPublicKey() throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    String jwtPublicKey =
        FileUtils.readFileToString(getCertificateFile("jwt.pub.pem"), Charset.defaultCharset());
    String encodedKey =
        jwtPublicKey
            .replace(Strings.BEGIN_PUBLIC_KEY, "")
            .replace(Strings.END_PUBLIC_KEY, "")
            .replace(System.lineSeparator(), "")
            .replace(" ", "")
            .trim();
    byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
    return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
  }

  private RSAPrivateKey getPrivateKey() throws Exception {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    String jwtPublicKey =
        FileUtils.readFileToString(getCertificateFile("jwt.priv.pem"), Charset.defaultCharset());
    String encodedKey =
        jwtPublicKey
            .replace(Strings.BEGIN_PRIVATE_KEY, "")
            .replace(Strings.END_PRIVATE_KEY, "")
            .replace(System.lineSeparator(), "")
            .replace(" ", "")
            .trim();
    byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
    return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
  }

  private String getRandomNumber(int digCount) {
    Random rnd = new Random();
    StringBuilder sb = new StringBuilder(digCount);
    for (int i = 0; i < digCount; i++) {
      sb.append((char) ('0' + rnd.nextInt(10)));
    }
    return sb.toString();
  }

  /**
   * Retrieves a file from either a local Docker container or directly from the mapped volume,
   * depending on the test runtime environment.
   *
   * @param name The name of the certificate file.
   * @return File instance of the certificate.
   * @throws Exception If file retrieval fails.
   */
  private File getCertificateFile(String name) throws Exception {
    if ("local".equalsIgnoreCase(env.getRuntime())) {
      // Use getFileInContainer for local development
      return CertificateUtils.getFileInContainer("file-orchestrator", "/storage/certs/" + name);
    } else {
      // Assuming this test code is run inside a docker container.
      return CertificateUtils.getFileFromLocalFolder("/storage/certs/", name);
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @AfterEach
  public void teardown() {
    rawFile.delete();
    encFile.delete();
  }

  private SSLContext createSslContext() throws Exception {
    // Load the PKCS12 trust store
    File rootCA = getCertificateFile("truststore.p12");
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(new FileInputStream(rootCA), env.getTruststore_password().toCharArray());
    // Create trust manager
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);
    // Create and initialize the SSLContext
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

    return sslContext;
  }
}

// keytool -list -v -keystore $JAVA_HOME/lib/security/cacerts
// docker cp file-orchestrator:/storage/certs/rootCA.pem .
// keytool -delete -alias fega -keystore $JAVA_HOME/lib/security/cacerts
// keytool -import -trustcacerts -file rootCA.pem -alias fega -keystore
// $JAVA_HOME/lib/security/cacerts
// replace_rootca file-orchestrator /storage/certs/rootCA.pem fega
