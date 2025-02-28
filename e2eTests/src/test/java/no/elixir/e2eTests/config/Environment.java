package no.elixir.e2eTests.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;

@Getter
public class Environment {

  // CEGAAUTH
  private final String ega_box_username;
  private final String ega_box_password;

  // CEGAMQ
  private final String broker_host;
  private final String broker_port;
  private final String broker_username;
  private final String broker_password;
  private final String broker_vhost;
  private final String broker_ssl_enabled;

  // PROXY
  private final String proxy_host;
  private final String proxy_port;

  // SDA DB
  private final String sda_db_username;
  private final String sda_db_password;
  private final String sda_db_host;
  private final String sda_db_port;
  private final String sda_doa_host;
  private final String sda_doa_port;
  private final String sda_db_database_name;

  // COMMON
  private final String truststore_password;
  private final String runtime;

  // Static Dotenv instance for accessing environment variables
  private static final Dotenv dotenv = Dotenv.load();

  // Constructor
  public Environment() {

    this.runtime = dotenv.get("RUNTIME");

    // Set variables based on RUNTIME
    //
    // When the runtime is set to local, the test setup assumes that the containers
    // or services are mapped to the host machine's network. In this configuration,
    // the application resolves services using localhost, allowing it to communicate
    // with the locally exposed ports of the containers or services.
    if ("local".equalsIgnoreCase(this.runtime)) {
      this.broker_host = "localhost";
      this.sda_db_host = "localhost";
      this.sda_doa_host = "localhost";
      this.proxy_host = "localhost";
    } else if ("container".equalsIgnoreCase(this.runtime)) {
      this.broker_host = dotenv.get("BROKER_HOST");
      this.sda_db_host = dotenv.get("SDA_DB_HOST");
      this.sda_doa_host = dotenv.get("SDA_DOA_HOST");
      this.proxy_host = dotenv.get("PROXY_HOST");
    } else {
      throw new IllegalArgumentException("Invalid RUNTIME value: " + this.runtime);
    }

    // Common variable initialization
    this.proxy_port = dotenv.get("PROXY_PORT");
    this.broker_port = dotenv.get("BROKER_PORT");
    this.broker_username = dotenv.get("BROKER_USERNAME");
    this.broker_password = dotenv.get("BROKER_PASSWORD");
    this.broker_vhost = dotenv.get("BROKER_VHOST");
    this.broker_ssl_enabled = dotenv.get("BROKER_SSL_ENABLED");
    this.sda_db_username = dotenv.get("SDA_DB_USERNAME");
    this.sda_db_password = dotenv.get("SDA_DB_PASSWORD");
    this.sda_db_database_name = dotenv.get("SDA_DB_DATABASE_NAME");
    this.ega_box_username = dotenv.get("EGA_BOX_USERNAME");
    this.ega_box_password = dotenv.get("EGA_BOX_PASSWORD");
    this.sda_db_port = dotenv.get("SDA_DB_PORT");
    this.sda_doa_port = dotenv.get("SDA_DOA_PORT");
    this.truststore_password = dotenv.get("TRUSTSTORE_PASSWORD");
  }

  // Method to construct and return the broker connection string
  public String getBrokerConnectionString() {
    String protocol = "true".equalsIgnoreCase(broker_ssl_enabled) ? "amqps" : "amqp";
    return String.format(
        "%s://%s:%s@%s:%s/%s",
        protocol, broker_username, broker_password, broker_host, broker_port, broker_vhost);
  }
}
