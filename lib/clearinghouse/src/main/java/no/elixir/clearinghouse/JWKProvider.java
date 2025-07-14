package no.elixir.clearinghouse;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/** Singleton class to be used for retrieving keys from JKU entry of JWT header. */
public enum JWKProvider {
  INSTANCE;

  private final OkHttpClient client = new OkHttpClient();

  private static final String KEYS = "keys";

  private final Gson gson = new Gson();

  private final LoadingCache<Pair<String, String>, Jwk> cache =
      Caffeine.newBuilder().maximumSize(100).build(this::getInternal);

  /**
   * Returns <code>Jwk</code> instance containing RSA Public Key with specified ID, fetched from
   * specified URL. The implementation uses cache.
   *
   * @param url JKU URL to fetch key from.
   * @param keyId Key ID.
   * @return <code>Jwk</code> instance.
   */
  public synchronized Jwk get(String url, String keyId) {
    return cache.get(new ImmutablePair<>(url, keyId));
  }

  private Jwk getInternal(Pair<String, String> urlAndId) throws Exception {
    var url = urlAndId.getKey();
    var keyId = urlAndId.getValue();
    return getAll(url).stream()
        .filter(k -> k.getId().equals(keyId))
        .findAny()
        .orElseThrow(() -> new Exception("No key found in " + url + " with kid " + keyId, null));
  }

  @SuppressWarnings("unchecked")
  private List<Jwk> getAll(String url) {
    Request request = new Request.Builder().url(url).get().build();
    JsonArray keysArray;
    try {
      ResponseBody body = client.newCall(request).execute().body();
      assert body != null;
      String bodyString = body.string();
      keysArray = gson.fromJson(bodyString, JsonObject.class).getAsJsonArray(KEYS);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return keysArray.asList().stream()
        .map(JsonElement::toString)
        .map(keyJson -> Jwks.parser().build().parse(keyJson))
        .collect(Collectors.toList());
  }
}
