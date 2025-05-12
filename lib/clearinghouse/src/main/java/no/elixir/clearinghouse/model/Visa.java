package no.elixir.clearinghouse.model;

import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

/** POJO representing Crypt4GH visa. */
@Data
@ToString
@NoArgsConstructor
public class Visa {

  private String sub; // JWT subject

  @NonNull private String type; // passport visa type

  @NonNull private Long asserted; // seconds since epoch

  @NonNull private String value; // value string

  @NonNull private String source; // source URL

  private List<List<Map<?, ?>>> conditions; // conditions

  private String by; // by identifier

  /**
   * The raw token required for APIs above the Clearing House to delegate tasks to other services
   * (such as SDA and DOA). This field is marked as transient to prevent it from being serialized
   * and exposed unintentionally during object serialization processes. The token is necessary for
   * internal operations but should not be persisted or transmitted in insecure contexts.
   *
   * <p>FIXME: We need to remove this in the future to enhance security and ensure sensitive
   * information is not exposed.
   */
  @ToString.Exclude private transient String rawToken;
}
