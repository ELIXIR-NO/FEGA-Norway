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

public abstract class BaseTest {

    public static void setupTestEnvironment() throws Exception {

        String basePath = "./";

        long fileSize = 1024 * 1024 * 10;
        State.log.info("Generating {} bytes file to submit...", fileSize);

        State.rawFile = CommonUtils.createRandomFile(basePath, fileSize);

        byte[] bytes = DigestUtils.sha256(Files.newInputStream(State.rawFile.toPath()));
        State.rawSHA256Checksum = Hex.encodeHexString(bytes);
        State.log.info("Raw SHA256 checksum: {}", State.rawSHA256Checksum);

        byte[] bytes2 = DigestUtils.md5(Files.newInputStream(State.rawFile.toPath()));
        State.rawMD5Checksum = Hex.encodeHexString(bytes2);
        State.log.info("Raw MD5 checksum: {}", State.rawMD5Checksum);

        State.log.info("Generating sender and recipient key-pairs...");
        KeyPair senderKeyPair = State.keyUtils.generateKeyPair();

        State.log.info("Encrypting the file with Crypt4GH...");
        State.encFile = new File(basePath + State.rawFile.getName() + ".enc");

        PublicKey localEGAInstancePublicKey = State.keyUtils.readPublicKey(CertificateUtils.getCertificateFile("ega.pub.pem"));

        try (FileOutputStream fileOutputStream = new FileOutputStream(State.encFile);
             Crypt4GHOutputStream crypt4GHOutputStream =
                     new Crypt4GHOutputStream(
                             fileOutputStream, senderKeyPair.getPrivate(), localEGAInstancePublicKey)) {
            FileUtils.copyFile(State.rawFile, crypt4GHOutputStream);
        }

        bytes = DigestUtils.sha256(Files.newInputStream(State.encFile.toPath()));
        State.encSHA256Checksum = Hex.encodeHexString(bytes);
        State.log.info("Enc SHA256 checksum: {}", State.encSHA256Checksum);

        try (UnirestInstance instance = Unirest.primaryInstance()) {
            instance.config().verifySsl(false).hostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }

    }

    public static void cleanupTestEnvironment() {
        if (!State.rawFile.delete() || !State.encFile.delete()) {
            throw new RuntimeException("Failed to delete temporary files");
        }
    }

}
