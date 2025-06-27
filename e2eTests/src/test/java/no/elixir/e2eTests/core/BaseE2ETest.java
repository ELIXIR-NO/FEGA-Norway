package no.elixir.e2eTests.core;

import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import no.elixir.crypt4gh.stream.Crypt4GHOutputStream;
import no.elixir.e2eTests.utils.CertificateUtils;
import no.elixir.e2eTests.utils.CommonUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.PublicKey;

public abstract class BaseE2ETest {

    public static void setupTestEnvironment() throws Exception {

        String basePath = "./";

        long fileSize = 1024 * 1024 * 10;
        E2EState.log.info("Generating {} bytes file to submit...", fileSize);

        E2EState.rawFile = CommonUtils.createRandomFile(basePath, fileSize);

        byte[] bytes = DigestUtils.sha256(Files.newInputStream(E2EState.rawFile.toPath()));
        E2EState.rawSHA256Checksum = Hex.encodeHexString(bytes);
        E2EState.log.info("Raw SHA256 checksum: {}", E2EState.rawSHA256Checksum);

        byte[] bytes2 = DigestUtils.md5(Files.newInputStream(E2EState.rawFile.toPath()));
        E2EState.rawMD5Checksum = Hex.encodeHexString(bytes2);
        E2EState.log.info("Raw MD5 checksum: {}", E2EState.rawMD5Checksum);

        E2EState.log.info("Generating sender and recipient key-pairs...");
        KeyPair senderKeyPair = E2EState.keyUtils.generateKeyPair();

        E2EState.log.info("Encrypting the file with Crypt4GH...");
        E2EState.encFile = new File(basePath + E2EState.rawFile.getName() + ".enc");

        PublicKey localEGAInstancePublicKey = E2EState.keyUtils.readPublicKey(CertificateUtils.getCertificateFile("ega.pub.pem"));

        try (FileOutputStream fileOutputStream = new FileOutputStream(E2EState.encFile);
             Crypt4GHOutputStream crypt4GHOutputStream =
                     new Crypt4GHOutputStream(
                             fileOutputStream, senderKeyPair.getPrivate(), localEGAInstancePublicKey)) {
            FileUtils.copyFile(E2EState.rawFile, crypt4GHOutputStream);
        }

        bytes = DigestUtils.sha256(Files.newInputStream(E2EState.encFile.toPath()));
        E2EState.encSHA256Checksum = Hex.encodeHexString(bytes);
        E2EState.log.info("Enc SHA256 checksum: {}", E2EState.encSHA256Checksum);

        try (UnirestInstance instance = Unirest.primaryInstance()) {
            instance.config().verifySsl(false).hostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }

    }

    public static void cleanupTestEnvironment() {
        if (!E2EState.rawFile.delete() || !E2EState.encFile.delete()) {
            throw new RuntimeException("Failed to delete temporary files");
        }
    }

}
