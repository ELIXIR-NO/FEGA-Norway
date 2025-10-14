package no.elixir.e2eTests.utils;

import com.auth0.jwt.algorithms.Algorithm;
import no.elixir.e2eTests.constants.Strings;
import no.elixir.e2eTests.core.E2EState;
import org.apache.commons.io.FileUtils;

import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class TokenUtils {

    public static String generateVisaToken(String resource,
                                           String pubKeyFilename,
                                           String privKeyFilename
    ) throws Exception {
        RSAPublicKey publicKey = getPublicKey(pubKeyFilename);
        RSAPrivateKey privateKey = getPrivateKey(privKeyFilename);
        byte[] visaHeader = Base64.getUrlEncoder().encode(Strings.VISA_HEADER.getBytes());
        byte[] visaPayload =
                Base64.getUrlEncoder()
                        .encode(
                                String.format(Strings.VISA_PAYLOAD, E2EState.env.getProxyTokenAudience(), resource)
                                        .getBytes());
        byte[] visaSignature = Algorithm.RSA256(publicKey, privateKey).sign(visaHeader, visaPayload);
        return "%s.%s.%s"
                .formatted(
                        new String(visaHeader),
                        new String(visaPayload),
                        Base64.getUrlEncoder().encodeToString(visaSignature));
    }

    public static RSAPublicKey getPublicKey(String pubKeyFilename) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        String jwtPublicKey =
                org.apache.commons.io.FileUtils.readFileToString(
                        CertificateUtils.getCertificateFile("jwt.pub.pem"), Charset.defaultCharset());
        String encoded = jwtPublicKey
                .replace(Strings.BEGIN_PUBLIC_KEY, "")
                .replace(Strings.END_PUBLIC_KEY, "")
                .replace(System.lineSeparator(), "")
                .replace(" ", "")
                .trim();
        byte[] decodedKey = Base64.getDecoder().decode(encoded);
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
    }

    public static RSAPrivateKey getPrivateKey(String privKeyFilename) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        String jwtPrivKey =
                FileUtils.readFileToString(
                        CertificateUtils.getCertificateFile("jwt.priv.pem"), Charset.defaultCharset());
        String encoded = jwtPrivKey
                .replace(Strings.BEGIN_PRIVATE_KEY, "")
                .replace(Strings.END_PRIVATE_KEY, "")
                .replace(System.lineSeparator(), "")
                .replace(" ", "")
                .trim();
        byte[] decodedKey = Base64.getDecoder().decode(encoded);
        return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
    }

}
