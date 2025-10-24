package no.elixir.e2eTests.config;

import lombok.Getter;

import java.util.Map;

@Getter
public class Environment {

    private final Map<String, String> env;

    private final String cegaAuthUsername;
    private final String cegaAuthPassword;
    private final String cegaConnString;
    private final String proxyHost;
    private final String proxyPort;
    private final String sdaDbUsername;
    private final String sdaDbPassword;
    private final String sdaDbHost;
    private final String sdaDbPort;
    private final String sdaDoaHost;
    private final String sdaDoaPort;
    private final String sdaDbDatabaseName;
    private final String truststorePassword;
    private final String runtime;
    private final String proxyTokenAudience;
    private final String proxyAdminUsername;
    private final String proxyAdminPassword;

    // Ega-Dev Test Environment Specific
    private final String egaDevBaseDirectory;

    private final String egaDevPubKeyPath;

    private final String egaDevJwtPubKeyPath;
    private final String egaDevJwtPrivKeyPath;

    // Common variable to override the generated access token
    private final String LSAAIToken;


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

        this.egaDevBaseDirectory = env.get("E2E_TESTS_EGA_DEV_BASE_DIRECTORY");
        this.egaDevJwtPubKeyPath = env.get("E2E_TESTS_EGA_DEV_JWT_PUB_KEYPATH"); // TODO: fix
        this.egaDevJwtPrivKeyPath = env.get("E2E_TESTS_EGA_DEV_JWT_PRIV_KEYPATH"); // TODO: fix
        this.egaDevPubKeyPath = env.get("E2E_TESTS_EGA_DEV_ARCHIVE_PUB_KEYPATH");
        this.LSAAIToken = env.get("E2E_TESTS_LSAAI_TOKEN");
    }

    public String getBrokerConnectionString() {
        return cegaConnString;
    }
}
