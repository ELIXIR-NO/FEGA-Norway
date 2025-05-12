package no.uio.ifi.clearinghouse;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureAlgorithm;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import lombok.Getter;
import no.uio.ifi.clearinghouse.model.Visa;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class CredentialsProvider {
  private final PrivateKey privateKey;
  @Getter private final PublicKey publicKey;
  @Getter private final String accessToken;
  @Getter private final String visaToken;
  @Getter private final String passportJsonString;

  public CredentialsProvider(String url) throws Exception {
    File privateKeyFile = new File("src/test/resources/private.pem");
    File publicKeyFile = new File("src/test/resources/public.pem");
    this.privateKey = readPrivateKey(privateKeyFile);
    this.publicKey = readPublicKey(publicKeyFile);

    this.accessToken = createAccessToken(url);
    this.visaToken = createVisaToken(url);

    this.passportJsonString = createPassportJsonString();
  }

  private String createAccessToken(String url) {
    SignatureAlgorithm alg = Jwts.SIG.RS512;
    return Jwts.builder()
        .header()
        .keyId("rsa1")
        .add("alg", "RS256")
        .and()
        .signWith(this.privateKey, alg)
        .subject("test@elixir-europe.org")
        .claim("azp", "e84ce6d6-a136-4654-8128-14f034ea24f7")
        .claim("scope", "ga4gh_passport_v1 openid")
        .audience()
        .add("e84ce6d6-a136-4654-8128-14f034ea24f7")
        .and()
        .issuer(url)
        .expiration(new Date(32503680000000L))
        .issuedAt(new Date())
        .id("03f5ca99-8df5-4d64-9dcb-7bf7701fe257")
        .compact();
  }

  private String createVisaToken(String url) {
    Visa visa = new Visa();
    visa.setBy("system");
    visa.setType("AffiliationAndRole");
    visa.setAsserted(1583757401L);
    visa.setSource("https://login.elixir-czech.org/google-idp/");
    visa.setValue("affiliate@google.com");

    SignatureAlgorithm alg = Jwts.SIG.RS512;
    return Jwts.builder()
        .header()
        .keyId("rsa1")
        .type("JWT")
        .add("jku", url + "jwk")
        .add("alg", "RS256")
        .and()
        .signWith(this.privateKey, alg)
        .subject("test@elixir-europe.org")
        .claim("ga4gh_visa_v1", visa)
        .issuer(url)
        .expiration(new Date(32503680000000L))
        .issuedAt(new Date())
        .id("f520d56f-e51a-431c-94e1-2a3f9da8b0c9")
        .compact();
  }

  private RSAPrivateKey readPrivateKey(File file) throws IOException {
    Security.addProvider(new BouncyCastleProvider());
    PEMParser pemParser = new PEMParser(new FileReader(file));
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
    Object object = pemParser.readObject();
    KeyPair kp = converter.getKeyPair((PEMKeyPair) object);

    return (RSAPrivateKey) kp.getPrivate();
  }

  private RSAPublicKey readPublicKey(File file) throws IOException {
    try (FileReader keyReader = new FileReader(file)) {
      PEMParser pemParser = new PEMParser(keyReader);
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(pemParser.readObject());
      return (RSAPublicKey) converter.getPublicKey(publicKeyInfo);
    }
  }

  // create passport.json w/ the newly generated visaToken
  private String createPassportJsonString() {
    return "{\n"
        + "  \"sub\": \"test@elixir-europe.org\",\n"
        + "  \"ga4gh_passport_v1\": [\n"
        + "    \""
        + this.visaToken
        + "\""
        + "  ]\n"
        + "}";
  }
}
