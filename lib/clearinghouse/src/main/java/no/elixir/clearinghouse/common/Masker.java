package no.elixir.clearinghouse.common;

public final class Masker {
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
}
