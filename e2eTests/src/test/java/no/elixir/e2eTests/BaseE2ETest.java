package no.elixir.e2eTests;

import com.auth0.jwt.algorithms.Algorithm;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import no.elixir.crypt4gh.stream.Crypt4GHOutputStream;
import no.elixir.crypt4gh.util.KeyUtils;
import no.elixir.e2eTests.config.Environment;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.utils.CertificateUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Random;

public abstract class BaseE2ETest {

    protected static final Environment env = new Environment();
    protected static final Logger log = LoggerFactory.getLogger(BaseE2ETest.class);
    protected static final KeyUtils keyUtils = KeyUtils.getInstance();

    protected File rawFile;
    protected File encFile;
    protected String rawSHA256Checksum;
    protected String encSHA256Checksum;
    protected String rawMD5Checksum;
    protected String stableId;
    protected String datasetId;
    protected String archivePath;
    protected String correlationId;

    protected void waitForProcessing(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
    }

    @BeforeAll
    protected void setupTestEnvironment() throws Exception {

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

    @AfterEach
    protected void cleanupTestEnvironment() {
        if (!rawFile.delete() || !encFile.delete()) {
            throw new RuntimeException("Failed to delete temporary files");
        }
    }

    // Utilities _>

    protected String getRandomNumber(int digCount) {
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(digCount);
        for (int i = 0; i < digCount; i++) {
            sb.append((char) ('0' + rnd.nextInt(10)));
        }
        return sb.toString();
    }

    // Certificates and SSL _>

    /**
     * Retrieves a file from either a local Docker container or directly from the mapped volume,
     * depending on the test runtime environment.
     *
     * @param name The name of the certificate file.
     * @return File instance of the certificate.
     * @throws Exception If file retrieval fails.
     */
    protected File getCertificateFile(String name) throws Exception {
        if ("local".equalsIgnoreCase(env.getRuntime())) {
            // Use getFileInContainer for local development
            return CertificateUtils.getFileInContainer("file-orchestrator", "/storage/certs/" + name);
        } else {
            // Assuming this test code is run inside a docker container.
            return CertificateUtils.getFileFromLocalFolder("/storage/certs/", name);
        }
    }

    /**
     * Creates ssl contexts for services such as: RabbitMQ
     *
     * @return SSLContext
     * @throws Exception Unable to load the store.
     */
    protected SSLContext createSslContext() throws Exception {
        // Load the PKCS12 trust store
        File rootCA = getCertificateFile("truststore.p12");
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(new FileInputStream(rootCA), env.getTruststorePassword().toCharArray());
        // Create trust manager
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        // Create and initialize the SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        return sslContext;
    }

    // Visas and Passport _>

    protected String generateVisaToken(String resource) throws Exception {
        RSAPublicKey publicKey = getPublicKey();
        RSAPrivateKey privateKey = getPrivateKey();
        byte[] visaHeader = Base64.getUrlEncoder().encode(Strings.VISA_HEADER.getBytes());
        byte[] visaPayload =
                Base64.getUrlEncoder()
                        .encode(
                                String.format(Strings.VISA_PAYLOAD, env.getProxyTokenAudience(), resource)
                                        .getBytes());
        byte[] visaSignature = Algorithm.RSA256(publicKey, privateKey).sign(visaHeader, visaPayload);
        return "%s.%s.%s"
                .formatted(
                        new String(visaHeader),
                        new String(visaPayload),
                        Base64.getUrlEncoder().encodeToString(visaSignature));
    }

    protected RSAPublicKey getPublicKey() throws Exception {
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

    protected RSAPrivateKey getPrivateKey() throws Exception {
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

}
