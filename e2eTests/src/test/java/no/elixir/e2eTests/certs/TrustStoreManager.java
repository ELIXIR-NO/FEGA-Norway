package no.elixir.e2eTests.certs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrustStoreManager {

  private static final String TRUSTSTORE_PASSWORD = "changeit";
  private static File originalTrustStore;
  private static String originalTrustStorePath;

  public static void configureSSL(File certificateFile) throws Exception {
    // 1. Backup original truststore settings
    originalTrustStorePath = System.getProperty("javax.net.ssl.trustStore");
    String javaHome = System.getProperty("java.home");
    originalTrustStore = new File(javaHome, "lib/security/cacerts");

    // 2. Create temporary truststore with custom certificate
    File tempTrustStore = createTempTrustStore(certificateFile);

    // 3. Configure system to use temporary truststore
    System.setProperty("javax.net.ssl.trustStore", tempTrustStore.getAbsolutePath());
    System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);

    // 4. Configure Unirest with explicit TLS protocol
    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(null, createTrustManager(tempTrustStore), new SecureRandom());

    Unirest.config().sslContext(sslContext).hostnameVerifier((host, session) -> true);
  }

  private static File createTempTrustStore(File certFile) throws Exception {
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());

    // Load original truststore or create empty
    try (InputStream is =
        originalTrustStore.exists() ? new FileInputStream(originalTrustStore) : null) {
      trustStore.load(is, TRUSTSTORE_PASSWORD.toCharArray());
    }

    // Add custom certificate
    Certificate cert =
        CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(certFile));
    trustStore.setCertificateEntry("dynamic-cert", cert);

    // Save to temporary file
    File tempFile = File.createTempFile("test-truststore", ".jks");
    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
      trustStore.store(fos, TRUSTSTORE_PASSWORD.toCharArray());
    }
    return tempFile;
  }

  private static TrustManager[] createTrustManager(File trustStoreFile) throws Exception {
    KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
    try (FileInputStream fis = new FileInputStream(trustStoreFile)) {
      ts.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
    }

    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ts);

    return tmf.getTrustManagers();
  }

  public static void resetSSL() {
    // Restore original truststore settings
    if (originalTrustStorePath != null) {
      System.setProperty("javax.net.ssl.trustStore", originalTrustStorePath);
    } else {
      System.clearProperty("javax.net.ssl.trustStore");
    }

    // Reset Unirest configuration
    Unirest.shutDown();
  }
}
