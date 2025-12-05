package no.elixir.fega.ltp.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Masker {

  public static String maskUsername(String username) {
    try {
      if (username.length() < 6) return username.replaceAll("(?<=.{2}).(?=.)", "*");
      else return username.replaceAll("(?<=.{3}).(?=.{2})", "*");
    } catch (Exception e) {
      return username;
    }
  }

  public static String maskEmail(String email) {
    try {
      String username = email.substring(0, email.indexOf("@"));
      // mask the username
      if (username.length() < 6) email = email.replaceAll("(?<=.{2}).(?=.*.{1}@)", "*");
      else email = email.replaceAll("(?<=.{3}).(?=.*.{2}@)", "*");
      // mask the domain
      return email.replaceAll("(?<=@)[^.]+(?=\\.[^.]+$)", "*****");
    } catch (Exception e) {
      return email;
    }
  }

  public static String maskEmailInPath(String path) {
    try {
      String regex = "/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})/";
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(path);
      if (matcher.find()) {
        return matcher.replaceFirst(
            Matcher.quoteReplacement("/" + maskEmail(matcher.group(1)) + "/"));
      }
      return path;
    } catch (Exception e) {
      return path;
    }
  }
}