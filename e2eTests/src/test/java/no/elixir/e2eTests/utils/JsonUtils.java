package no.elixir.e2eTests.utils;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

public class JsonUtils {

  public static String toCompactJson(String jsonString) {
    Gson gson = new Gson();
    return gson.toJson(JsonParser.parseString(jsonString));
  }
}
