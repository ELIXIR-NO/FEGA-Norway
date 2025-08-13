package no.elixir.e2eTests.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

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

  public Environment() {
    this.env = loadEnvFromShellScript();
    this.runtime = env.get("E2E_RUNTIME");
    this.cegaAuthUsername = env.get("E2E_CEGAAUTH_USERNAME");
    this.cegaAuthPassword = env.get("E2E_CEGAAUTH_PASSWORD");
    this.cegaConnString = env.get("E2E_CEGAMQ_CONN_STR");
    this.proxyHost = env.get("E2E_PROXY_HOST");
    this.proxyPort = env.get("E2E_PROXY_PORT");
    this.sdaDbHost = env.get("E2E_SDA_DB_HOST");
    this.sdaDbPort = env.get("E2E_SDA_DB_PORT");
    this.sdaDbUsername = env.get("E2E_SDA_DB_USERNAME");
    this.sdaDbPassword = env.get("E2E_SDA_DB_PASSWORD");
    this.sdaDbDatabaseName = env.get("E2E_SDA_DB_DATABASE_NAME");
    this.sdaDoaHost = env.get("E2E_SDA_DOA_HOST");
    this.sdaDoaPort = env.get("E2E_SDA_DOA_PORT");
    this.truststorePassword = env.get("E2E_TRUSTSTORE_PASSWORD");
    this.proxyTokenAudience = env.get("E2E_PROXY_TOKEN_AUDIENCE");
    this.proxyAdminUsername = env.get("E2E_PROXY_ADMIN_USERNAME");
    this.proxyAdminPassword = env.get("E2E_PROXY_ADMIN_PASSWORD");
  }

  private Map<String, String> loadEnvFromShellScript() {
    Map<String, String> envMap = new HashMap<>();
    try {
      String[] cmd = {"/bin/bash", "-c", "source " + "env.sh" + " && env"};
      Process process = new ProcessBuilder(cmd).start();

      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

      String line;
      while ((line = reader.readLine()) != null) {
        int idx = line.indexOf('=');
        if (idx != -1) {
          String key = line.substring(0, idx);
          String value = line.substring(idx + 1);
          envMap.put(key, value);
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new RuntimeException("Failed to source environment: exit code " + exitCode);
      }

    } catch (Exception e) {
      throw new RuntimeException("Error loading env from shell script", e);
    }

    return envMap;
  }

  public String getBrokerConnectionString() {
    return cegaConnString;
  }
}
