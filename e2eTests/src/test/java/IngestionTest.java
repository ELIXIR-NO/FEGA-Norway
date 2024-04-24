import com.auth0.jwt.algorithms.Algorithm;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import no.elixir.crypt4gh.stream.Crypt4GHInputStream;
import no.elixir.crypt4gh.stream.Crypt4GHOutputStream;
import no.elixir.crypt4gh.util.KeyUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class IngestionTest {

    private static final Logger log = LoggerFactory.getLogger(IngestionTest.class);

    public static final String BEGIN_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----";
    public static final String END_PUBLIC_KEY = "-----END PUBLIC KEY-----";
    public static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    public static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";

    private static final String DB_USERNAME = "postgres";
    private static final String DB_PASSWORD = "ro0tpasswd";
    private static final String EGA_BOX_USERNAME = "dummy";
    private static final String EGA_BOX_PASSWORD = "dummy";
    private static final String CEGA_MQ_CONN = "amqp://test:test@localhost:5673/lega?cacertfile=rootCA.pem";

    private final KeyUtils keyUtils = KeyUtils.getInstance();

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
    public void setup() throws IOException, GeneralSecurityException {

        long fileSize = 1024 * 1024 * 10;
        log.info("Generating " + fileSize + " bytes file to submit...");
        rawFile = new File(UUID.randomUUID() + ".raw");
        RandomAccessFile randomAccessFile = new RandomAccessFile(rawFile, "rw");
        randomAccessFile.setLength(fileSize);
        randomAccessFile.close();

        byte[] bytes = DigestUtils.sha256(Files.newInputStream(rawFile.toPath()));
        rawSHA256Checksum = Hex.encodeHexString(bytes);
        log.info("Raw SHA256 checksum: " + rawSHA256Checksum);

        byte[] bytes2 = DigestUtils.md5(Files.newInputStream(rawFile.toPath()));
        rawMD5Checksum = Hex.encodeHexString(bytes2);
        log.info("Raw MD5 checksum: " + rawMD5Checksum);

        log.info("Generating sender and recipient key-pairs...");
        KeyPair senderKeyPair = keyUtils.generateKeyPair();

        log.info("Encrypting the file with Crypt4GH...");
        encFile = new File(rawFile.getName() + ".enc");

        PublicKey localEGAInstancePublicKey = keyUtils.readPublicKey(
                new File(getCertificateLocation("ega.pub.pem"))
        );

        try (FileOutputStream fileOutputStream = new FileOutputStream(encFile);
             Crypt4GHOutputStream crypt4GHOutputStream =
                     new Crypt4GHOutputStream(
                             fileOutputStream,
                             senderKeyPair.getPrivate(),
                             localEGAInstancePublicKey)
        ) {
            FileUtils.copyFile(rawFile, crypt4GHOutputStream);
        }

        bytes = DigestUtils.sha256(Files.newInputStream(encFile.toPath()));
        encSHA256Checksum = Hex.encodeHexString(bytes);
        log.info("Enc SHA256 checksum: " + encSHA256Checksum);

        try (UnirestInstance instance = Unirest.primaryInstance()) {
            instance.config()
                    .verifySsl(false)
                    .hostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }

    }

    @Test
    public void performEndToEndTest() {
        try {
            upload();
            Thread.sleep(5000); // Wait for triggers to be set up at CEGA - Not really needed if using local CEGA container
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
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            fail();
        }
    }

    private void upload() throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        log.info("Uploading a file through a proxy...");
        String token = generateVisaToken("upload");
        log.info("Visa JWT token: {}", token);
        String md5Hex = DigestUtils.md5Hex(Files.newInputStream(encFile.toPath()));
        log.info("Encrypted MD5 checksum: {}", md5Hex);
        log.info("EGA_BOX_USERNAME: {}", EGA_BOX_USERNAME);
        String uploadURL =
                String.format("https://localhost:10443/stream/%s?md5=%s", encFile.getName(), md5Hex);
        JsonNode jsonResponse =
                Unirest.patch(uploadURL)
                        .basicAuth(EGA_BOX_USERNAME, EGA_BOX_PASSWORD)
                        .header("Proxy-Authorization", "Bearer " + token)
                        .body(FileUtils.readFileToByteArray(encFile))
                        .asJson()
                        .getBody();
        String uploadId = jsonResponse.getObject().getString("id");
        log.info("Upload ID: {}", uploadId);
        String finalizeURL =
                String.format(
                        "https://localhost:10443/stream/%s?uploadId=%s&chunk=end&sha256=%s&fileSize=%s",
                        encFile.getName(), uploadId, encSHA256Checksum, FileUtils.sizeOf(encFile));
        jsonResponse =
                Unirest.patch(finalizeURL)
                        .basicAuth(EGA_BOX_USERNAME, EGA_BOX_PASSWORD)
                        .header("Proxy-Authorization", "Bearer " + token)
                        .asJson()
                        .getBody();
        assertEquals(201, jsonResponse.getObject().getInt("statusCode"));
    }

    private void triggerIngestMessageFromCEGA()
            throws IOException,
            TimeoutException,
            NoSuchAlgorithmException,
            KeyManagementException,
            URISyntaxException {
        log.info("Publishing ingestion message to CentralEGA...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(CEGA_MQ_CONN);
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

        String message = """
                  {
                    "type": "ingest",
                    "user": "%s",
                    "filepath": "/p11-dummy@elixir-europe.org/files/%s"
                  }
                """.formatted(EGA_BOX_USERNAME, encFile.getName());
        log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());

        channel.close();
        connectionFactory.close();
    }

    private void triggerAccessionMessageFromCEGA()
            throws IOException,
            TimeoutException,
            NoSuchAlgorithmException,
            KeyManagementException,
            URISyntaxException {
        log.info("Publishing accession message on behalf of CEGA to CEGA RMQ...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(CEGA_MQ_CONN);
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

        String message = String.format("""
                        {
                            "type": "accession",
                            "user": "%s",
                            "filepath": "/p11-dummy@elixir-europe.org/files/%s",
                            "accession_id": "%s",
                            "decrypted_checksums": [
                                {
                                    "type": "sha256",
                                    "value": "%s"
                                },
                                {
                                    "type": "md5",
                                    "value": "%s"
                                }
                            ]
                        }""",
                EGA_BOX_USERNAME,
                encFile.getName(),
                randomFileAccessionID,
                rawSHA256Checksum,
                rawMD5Checksum
        );

        log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());

        channel.close();
        connectionFactory.close();
    }

    @SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
    private void verifyAfterFinalizeAndLookUpAccessionID() throws SQLException {
        log.info("Starting verification of state after finalize step...");
        String dbHost = "localhost";
        String port = "5432";
        String db = "sda";
        String url = String.format("jdbc:postgresql://%s:%s/%s", dbHost, port, db);
        Properties props = new Properties();
        props.setProperty("user", DB_USERNAME);
        props.setProperty("password", DB_PASSWORD);
        props.setProperty("ssl", "true");
        props.setProperty("application_name", "LocalEGA");
        props.setProperty("sslmode", "verify-ca");
        props.setProperty("sslrootcert", new File("rootCA.pem").getAbsolutePath());
        props.setProperty("sslcert", new File("localhost+5-client.pem").getAbsolutePath());
        props.setProperty("sslkey", new File("localhost+5-client-key.der").getAbsolutePath());
        java.sql.Connection conn = DriverManager.getConnection(url, props);
        String sql = "select archive_path,stable_id from local_ega.files where status = 'COMPLETED' AND inbox_path = ?";
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

    private void triggerMappingMessageFromCEGA()
            throws NoSuchAlgorithmException,
            KeyManagementException,
            URISyntaxException,
            IOException,
            TimeoutException {
        log.info("Mapping file to a dataset...");
        datasetId = "EGAD" + getRandomNumber(11);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(CEGA_MQ_CONN);
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
        String message = String.format("""
                        {
                            "type": "mapping",
                            "accession_ids": ["%s"],
                            "dataset_id": "%s"
                        }""",
                stableId,
                datasetId
        );
        log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());

        channel.close();
        connectionFactory.close();
        log.info("Mapping file to dataset ID message sent successfully");
    }

    private void triggerReleaseMessageFromCEGA()
            throws NoSuchAlgorithmException,
            KeyManagementException,
            URISyntaxException,
            IOException,
            TimeoutException {
        log.info("Releasing the dataset...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(CEGA_MQ_CONN);
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
        String message = String.format("""
                    {"type":"release","dataset_id":"%s"}
                """, datasetId);
        log.info(message);
        channel.basicPublish("localega", "files", properties, message.getBytes());
        channel.close();
        connectionFactory.close();
        log.info("Dataset release message sent successfully");
    }

    private void downloadDatasetAndVerifyResults() throws GeneralSecurityException, IOException {

        String token = generateVisaToken(datasetId);
        log.info("Visa JWT token: {}", token);

        String datasets =
                Unirest.get("http://localhost/metadata/datasets")
                        .header("Authorization", "Bearer " + token)
                        .asString()
                        .getBody();
        assertEquals(String.format("[\"%s\"]", datasetId).strip(), datasets.strip());

        // Meta data check
        String expected = String.format("""
                        [{
                            "fileId": "%s",
                            "datasetId": "%s",
                            "displayFileName": "%s",
                            "fileName": "%s",
                            "fileSize": 10490240,
                            "unencryptedChecksum": null,
                            "unencryptedChecksumType": null,
                            "decryptedFileSize": 10485760,
                            "decryptedFileChecksum": "%s",
                            "decryptedFileChecksumType": "SHA256",
                            "fileStatus": "READY"
                        }]
                        """,
                stableId, datasetId, encFile.getName(), archivePath, rawSHA256Checksum
        ).strip();
        String actual =
                Unirest.get(String.format("http://localhost/metadata/datasets/%s/files", datasetId))
                        .header("Authorization", "Bearer " + token)
                        .asString()
                        .getBody()
                        .strip();
        log.info("Expected: {}", expected);
        log.info("Actual: {}", actual);
        assertEquals(expected, actual);

        byte[] file =
                Unirest.get(String.format("http://localhost/files/%s", stableId))
                        .header("Authorization", "Bearer " + token)
                        .asBytes()
                        .getBody();
        String obtainedChecksum = Hex.encodeHexString(DigestUtils.sha256(file));
        assertEquals(rawSHA256Checksum, obtainedChecksum);

        KeyPair recipientKeyPair = keyUtils.generateKeyPair();
        StringWriter stringWriter = new StringWriter();
        keyUtils.writeCrypt4GHKey(stringWriter, recipientKeyPair.getPublic(), null);
        String key = stringWriter.toString();
        file =
                Unirest.get(String.format("http://localhost/files/%s?destinationFormat=CRYPT4GH", stableId))
                        .header("Authorization", "Bearer " + token)
                        .header("Public-Key", key)
                        .asBytes()
                        .getBody();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(file);
             Crypt4GHInputStream crypt4GHInputStream =
                     new Crypt4GHInputStream(byteArrayInputStream, recipientKeyPair.getPrivate())) {
            IOUtils.copyLarge(crypt4GHInputStream, byteArrayOutputStream);
        }
        obtainedChecksum = Hex.encodeHexString(DigestUtils.sha256(byteArrayOutputStream.toByteArray()));
        assertEquals(rawSHA256Checksum, obtainedChecksum);
    }

    private String generateVisaToken(String resource)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        RSAPublicKey publicKey = getPublicKey();
        RSAPrivateKey privateKey = getPrivateKey();
        byte[] visaHeader =
                Base64.getUrlEncoder()
                        .encode(
                                ("""
                                        {
                                          "jku": "https://login.elixir-czech.org/oidc/jwk",
                                          "kid": "rsa1",
                                          "typ": "JWT",
                                          "alg": "RS256"
                                        }""")
                                        .getBytes());
        byte[] visaPayload =
                Base64.getUrlEncoder()
                        .encode(
                                String.format(
                                                """
                                                        {
                                                          "sub": "dummy@elixir-europe.org",
                                                          "ga4gh_visa_v1": {
                                                            "asserted": 1583757401,
                                                            "by": "dac",
                                                            "source": "https://login.elixir-czech.org/google-idp/",
                                                            "type": "ControlledAccessGrants",
                                                            "value": "https://ega.tsd.usit.uio.no/datasets/%s/"
                                                          },
                                                          "iss": "https://login.elixir-czech.org/oidc/",
                                                          "exp": 32503680000,
                                                          "iat": 1583757671,
                                                          "jti": "f520d56f-e51a-431c-94e1-2a3f9da8b0c9"
                                                        }""",
                                                resource)
                                        .getBytes());
        byte[] visaSignature = Algorithm.RSA256(publicKey, privateKey).sign(visaHeader, visaPayload);
        return new String(visaHeader)
                + "."
                + new String(visaPayload)
                + "."
                + Base64.getUrlEncoder().encodeToString(visaSignature);
    }

    private RSAPublicKey getPublicKey()
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        String jwtPublicKey =
                FileUtils.readFileToString(
                        new File(getCertificateLocation("jwt.pub.pem")), Charset.defaultCharset());
        String encodedKey =
                jwtPublicKey
                        .replace(BEGIN_PUBLIC_KEY, "")
                        .replace(END_PUBLIC_KEY, "")
                        .replace(System.lineSeparator(), "")
                        .replace(" ", "")
                        .trim();
        byte[] decodedKey = Base64.getUrlDecoder().decode(encodedKey);
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
    }

    private RSAPrivateKey getPrivateKey()
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        String jwtPublicKey =
                FileUtils.readFileToString(
                        new File(getCertificateLocation("jwt.priv.pem")), Charset.defaultCharset());
        String encodedKey =
                jwtPublicKey
                        .replace(BEGIN_PRIVATE_KEY, "")
                        .replace(END_PRIVATE_KEY, "")
                        .replace(System.lineSeparator(), "")
                        .replace(" ", "")
                        .trim();
        byte[] decodedKey = Base64.getUrlDecoder().decode(encodedKey);
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

    private String getCertificateLocation(String name) {
        return String.format("./tmp/certs/%s", name);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @AfterEach
    public void teardown() {
        rawFile.delete();
        encFile.delete();
    }

}
