package no.elixir.fega.ltp.common;

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
      String username = email.substring(0, email.indexOf('@'));
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
      String[] partsInPath = path.split("/", -1);
      String[] partsInEmail = partsInPath[1].split("-", 2);
      System.out.println(partsInEmail[1]);
      partsInEmail[1] = maskEmail(partsInEmail[1]);
      partsInPath[1] = String.join("-", partsInEmail);
      return String.join("/", partsInPath);
    } catch (Exception e) {
      return path;
    }
  }
}
