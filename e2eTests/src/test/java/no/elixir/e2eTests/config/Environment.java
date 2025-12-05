package no.elixir.e2eTests.config;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Environment {

  private Map<String, String> env;

  private String cegaAuthUsername;
  private String cegaAuthPassword;
  private String cegaConnString;
  private String proxyHost;
  private String proxyPort;
  private String sdaDbUsername;
  private String sdaDbPassword;
  private String sdaDbHost;
  private String sdaDbPort;
  private String sdaDoaHost;
  private String sdaDoaPort;
  private String sdaDbDatabaseName;
  private String truststorePassword;
  private String runtime;
  private String proxyTokenAudience;
  private String proxyAdminUsername;
  private String proxyAdminPassword;
  private int exportRequestMaxRetries;
  private long exportRequestIntervalInSeconds;

  // Ega-Dev Test Environment Specific
  private String egaDevBaseDirectory;

  private String egaDevPubKeyPath;

  private String egaDevJwtPubKeyPath;
  private String egaDevJwtPrivKeyPath;

  // Common variable to override the generated access token
  private String LSAAIToken;

  private String tsdProject;
  private String lsaaiSubject;

  public Environment() {
    this.env = System.getenv();
    this.runtime = env.get("E2E_TESTS_RUNTIME");
    this.cegaAuthUsername = env.get("E2E_TESTS_CEGAAUTH_USERNAME");
    this.cegaAuthPassword = env.get("E2E_TESTS_CEGAAUTH_PASSWORD");
    this.cegaConnString = env.get("E2E_TESTS_CEGAMQ_CONN_STR");
    this.proxyHost = env.get("E2E_TESTS_PROXY_HOST");
    this.proxyPort = env.get("E2E_TESTS_PROXY_PORT");
    this.sdaDbHost = env.get("E2E_TESTS_SDA_DB_HOST");
    this.sdaDbPort = env.get("E2E_TESTS_SDA_DB_PORT");
    this.sdaDbUsername = env.get("E2E_TESTS_SDA_DB_USERNAME");
    this.sdaDbPassword = env.get("E2E_TESTS_SDA_DB_PASSWORD");
    this.sdaDbDatabaseName = env.get("E2E_TESTS_SDA_DB_DATABASE_NAME");
    this.sdaDoaHost = env.get("E2E_TESTS_SDA_DOA_HOST");
    this.sdaDoaPort = env.get("E2E_TESTS_SDA_DOA_PORT");
    this.truststorePassword = env.get("E2E_TESTS_TRUSTSTORE_PASSWORD");
    this.proxyTokenAudience = env.get("E2E_TESTS_PROXY_TOKEN_AUDIENCE");
    this.proxyAdminUsername = env.get("E2E_TESTS_PROXY_ADMIN_USERNAME");
    this.proxyAdminPassword = env.get("E2E_TESTS_PROXY_ADMIN_PASSWORD");
    this.exportRequestMaxRetries =
        Integer.parseInt(env.get("E2E_TESTS_EXPORT_REQUEST_MAX_RETRIES"));
    this.exportRequestIntervalInSeconds =
        Long.parseLong(env.get("E2E_TESTS_EXPORT_REQUEST_INTERVAL_IN_SECONDS"));

    this.egaDevBaseDirectory = env.get("E2E_TESTS_EGA_DEV_BASE_DIRECTORY");
    this.egaDevJwtPubKeyPath = env.get("E2E_TESTS_EGA_DEV_JWT_PUB_KEYPATH");
    this.egaDevJwtPrivKeyPath = env.get("E2E_TESTS_EGA_DEV_JWT_PRIV_KEYPATH");
    this.egaDevPubKeyPath = env.get("E2E_TESTS_EGA_DEV_ARCHIVE_PUB_KEYPATH");
    this.LSAAIToken = env.get("E2E_TESTS_LSAAI_TOKEN");
    this.tsdProject = env.get("E2E_TESTS_TSD_PROJECT");
    this.lsaaiSubject = env.get("E2E_TESTS_LSAAI_SUBJECT");
  }

  public String getBrokerConnectionString() {
    return cegaConnString;
  }
}
