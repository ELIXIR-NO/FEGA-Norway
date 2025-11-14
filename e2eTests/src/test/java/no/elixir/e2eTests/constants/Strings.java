package no.elixir.e2eTests.constants;

public class Strings {

  public static final String BEGIN_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----";
  public static final String END_PUBLIC_KEY = "-----END PUBLIC KEY-----";
  public static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
  public static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";

  // JWT Header constants
  public static final String JWT_JKU = "https://login.elixir-czech.org/oidc/jwk";
  public static final String JWT_KID = "rsa1";
  public static final String JWT_TYP = "JWT";
  public static final String JWT_ALG = "RS256";

  // JWT Payload constants
  public static final String JWT_SUBJECT = "dummy@elixir-europe.org";
  public static final String JWT_ISSUER = "https://login.elixir-czech.org/oidc/";
  public static final String JWT_ID = "f520d56f-e51a-431c-94e1-2a3f9da8b0c9";
  public static final long JWT_EXPIRATION = 32503680000L;
  public static final long JWT_ISSUED_AT = 1583757671L;

  // GA4GH Visa constants
  public static final long VISA_ASSERTED = 1583757401L;
  public static final String VISA_BY = "dac";
  public static final String VISA_SOURCE = "https://login.elixir-czech.org/google-idp/";
  public static final String VISA_TYPE = "ControlledAccessGrants";
  public static final String VISA_VALUE_TEMPLATE = "https://ega.tsd.usit.uio.no/datasets/%s/";

  public static final String INGEST_MESSAGE =
      """
              {
                "type": "ingest",
                "user": "%s",
                "filepath": "/p11-dummy@elixir-europe.org/files/%s"
              }
            """;

  public static final String ACCESSION_MESSAGE =
      """
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
            }""";

  public static final String MAPPING_MESSAGE =
      """
            {
                "type": "mapping",
                "accession_ids": ["%s"],
                "dataset_id": "%s"
            }""";

  public static final String RELEASE_MESSAGE =
      """
                {"type":"release","dataset_id":"%s"}
            """;

  public static final String EXPECTED_DOWNLOAD_METADATA =
      """
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
            """;

  public static final String EXPORT_REQ_BODY =
      """
          {
              "id": "%s",
              "accessToken": "%s",
              "userPublicKey": "%s",
              "type": "%s"
          }
          """;
}
