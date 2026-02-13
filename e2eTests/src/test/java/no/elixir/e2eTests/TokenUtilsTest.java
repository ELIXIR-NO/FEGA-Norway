package no.elixir.e2eTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import no.elixir.e2eTests.utils.TokenUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TokenUtilsTest {

  @TempDir Path tempDir;

  @Test
  void testPKCS1PrivateKey() throws Exception {
    String pkcs1PrivateKey =
        """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEAsn+9in5AY3xOauxYljtWFocBEg/SVgTdGDQc/zLPHv2cwPeT
                8sidSRofN0UTDH6logNmf5drZlpBUPNFE4lZ24DlLbPZzD1K+ubLAWAHEYUVaDz7
                DjiVj5bBQUYMRqPXaz1/tfA5IMXfrmPCqhO2r+6OEXt6C9konsBRGNnGvfSCU8pP
                VMxe3OSBz8f57OHjBa5ryZfEBrAFLAB09s3oicbvrfcUjVRxFjUiUW8tHv+gSPVM
                8RDxpknwAvBDZ7kr0/xyIbRuNyuLQ7YQoPKf1M25EXqTR6uNaoHrJR9wX1T2757L
                IeVjKBiGzNYBTqV8xEd1Ht2jfzAvKYbdcuglqQIDAQABAoIBAGj/uWg5QfkDi0Fc
                S/P7bXWM7rVIN0tASypMpW383ld1ifr857ueBN+WjRw+9qiX6yi1ZVrSoGux1dAf
                ede3KPN1C9rpe8mnmG6kym/BpAbLST9q96dy4492d7HE2b3H7RT5NSG58HD11NFJ
                f536QLGW210vTCiiEkHoEQxUNpNPv/Fy+jRxl7TT/i+8wW60xBK0aDcfDNgNVsRq
                yiCmfoHzbdDJy+4vaBhnB0UH6zPJlNjFNX1187TBZoh9ysN2+FBUfWQDkTHafB+A
                frLNvnzdkNlbx6ZLEeEZHZYmBslPIG9bv5TMlZJofhInOK+5ep+HkWDlR/hXMWtC
                er6Wcl0CgYEA5/W5Oudk4Rk1PeHYvtnRxEI7aDFJJcxL99M1iADysPGDCyw/ndDy
                yX55DcIKmuw2SyPQhTLbkVdBorinKP2gO7dn6bVXUJro/TQnllnwh627CY2sLXEO
                E3H2OkxcGD0FMLxfvOVSgYJVIBRmWoXA7i0yVykmdY8pUuRTnQVEYZsCgYEAxP+c
                rFV5GdxxTxL2bZDwo4Zi3WADa7Fa6nqaSV4ORnyRYEq1eTg0pKORPNWujv/lChdZ
                0sFXBCIU8ZCewgwApfRfegsL9BPbxcggbCM9PWU8H+MHEXg4ticyEm2MGGVQvSHy
                SoyT+za0y6VczoJGqzsW8Zxy5vPdLDFpenkRHAsCgYB22w0fpqOlR9JlNA1otZnr
                s3hXSjHOAWHHydy7JKk2i3n3RqQOTJurLXf/2bavbWvgkqhtKAMj9Q73vyBAO71X
                /AFt2nK0JbxOyeOjAdDi5N1a5tIbsrtgsVtWwfvKm7HGKC/yTZs6KztNJcbQiYqz
                QEt0J+zB/ipRhBdn9OP2EwKBgQCHwYrLa/+PZc2j63Js4QQj/Jkm62KQFWGM0r5+
                LtXxlyhrzjAvAB+vEZUl8i9gFlJVWPAqf9giXgZGzh3JpZHQy438QZ6ULhc2vgox
                2zr5k0kSxFX7oPib9njYICv6J9+y5rDc1PGpnfKLoEJLgZWHrKnMCL9aDtXs/YQt
                xT6YLwKBgB2TBL6Z0kajrw77RTG5WPANGUEYLrDkfq5LPQcwJ/4rs56nUuW1jeeH
                GVe1YDAIWEoCxYS/sRg8SuTxF1AZQ4rgeNZLJzgBepOQmie4tySlJYkoww7YvCwP
                a/SdoUL52S2+Gl2N6qbgRaCvVKj5v8yStfMeFLzWcVxdlAnhZew1
                -----END RSA PRIVATE KEY-----
                """;
    String publicKey =
        """
                -----BEGIN PUBLIC KEY-----
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsn+9in5AY3xOauxYljtW
                FocBEg/SVgTdGDQc/zLPHv2cwPeT8sidSRofN0UTDH6logNmf5drZlpBUPNFE4lZ
                24DlLbPZzD1K+ubLAWAHEYUVaDz7DjiVj5bBQUYMRqPXaz1/tfA5IMXfrmPCqhO2
                r+6OEXt6C9konsBRGNnGvfSCU8pPVMxe3OSBz8f57OHjBa5ryZfEBrAFLAB09s3o
                icbvrfcUjVRxFjUiUW8tHv+gSPVM8RDxpknwAvBDZ7kr0/xyIbRuNyuLQ7YQoPKf
                1M25EXqTR6uNaoHrJR9wX1T2757LIeVjKBiGzNYBTqV8xEd1Ht2jfzAvKYbdcugl
                qQIDAQAB
                -----END PUBLIC KEY-----
                """;
    // Write keys to temp files
    File privKeyFile = new File(tempDir.toFile(), "pkcs1_private.pem");
    File pubKeyFile = new File(tempDir.toFile(), "pkcs1_public.pem");
    FileUtils.writeStringToFile(privKeyFile, pkcs1PrivateKey, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(pubKeyFile, publicKey, StandardCharsets.UTF_8);
    // Test loading PKCS#1 key (should be handled by BouncyCastle)
    RSAPrivateKey privateKey = TokenUtils.getPrivateKey(privKeyFile.getAbsolutePath());
    RSAPublicKey publicKey1 = TokenUtils.getPublicKey(pubKeyFile.getAbsolutePath());
    assertNotNull(privateKey);
    assertNotNull(publicKey1);
    assertEquals("RSA", privateKey.getAlgorithm());
    assertEquals("RSA", publicKey1.getAlgorithm());
  }
}
