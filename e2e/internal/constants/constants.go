// Package constants holds the JWT/visa constants and the AMQP/HTTP message
// templates. These values are wire contracts the proxy and SDA parse verbatim,
// so they must not be reformatted casually.
package constants

const (
	BeginPublicKey = "-----BEGIN PUBLIC KEY-----"
	EndPublicKey   = "-----END PUBLIC KEY-----"

	// JWT header constants.
	JWTJku = "https://login.elixir-czech.org/oidc/jwk"
	JWTKid = "rsa1"
	JWTTyp = "JWT"
	JWTAlg = "RS256"

	// JWT payload constants.
	JWTSubject          = "dummy@elixir-europe.org"
	JWTIssuer           = "https://login.elixir-czech.org/oidc/"
	JWTID               = "f520d56f-e51a-431c-94e1-2a3f9da8b0c9"
	JWTExpiration int64 = 32503680000
	JWTIssuedAt   int64 = 1583757671

	// GA4GH visa constants.
	VisaAsserted      int64 = 1583757401
	VisaBy                  = "dac"
	VisaSource              = "https://login.elixir-czech.org/google-idp/"
	VisaType                = "ControlledAccessGrants"
	VisaValueTemplate       = "https://ega.tsd.usit.uio.no/datasets/%s/"
)

// IngestMessage is the ingest event body. Format args: user, encFileName.
const IngestMessage = `{
                "type": "ingest",
                "user": "%s",
                "filepath": "/files/%s"
              }
`

// AccessionMessage is the accession event body. Format args: user, encFileName,
// accessionID, sha256, md5.
const AccessionMessage = `{
                "type": "accession",
                "user": "%s",
                "filepath": "/files/%s",
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
            }`

// MappingMessage is the mapping event body. Format args: stableID, datasetID.
const MappingMessage = `{
                "type": "mapping",
                "accession_ids": ["%s"],
                "dataset_id": "%s"
            }`

// ReleaseMessage is the dataset release event body. Format args: datasetID.
const ReleaseMessage = `{"type":"release","dataset_id":"%s"}
`

// ExpectedDownloadMetadata is the DOA file-metadata response template. Format
// args: fileID, datasetID, displayFileName, fileName, decryptedFileChecksum.
const ExpectedDownloadMetadata = `[{
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
`

// ExportReqBodyFEGA is the /export/fega request body. Format args: datasetID,
// visaToken, userPublicKey, type.
const ExportReqBodyFEGA = `{
                    "id": "%s",
                    "visaToken": "%s",
                    "userPublicKey": "%s",
                    "type": "%s"
                }
`
