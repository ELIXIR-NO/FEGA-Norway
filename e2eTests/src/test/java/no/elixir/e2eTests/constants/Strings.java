package no.elixir.e2eTests.constants;

public class Strings {

  public static final String BEGIN_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----";
  public static final String END_PUBLIC_KEY = "-----END PUBLIC KEY-----";
  public static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
  public static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";

  public static final String VISA_HEADER =
      """
            {
              "jku": "https://login.elixir-czech.org/oidc/jwk",
              "kid": "rsa1",
              "typ": "JWT",
              "alg": "RS256"
            }""";

  public static final String VISA_PAYLOAD =
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
            }""";

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
}
