package no.elixir.crypt4gh.app;

import java.io.Console;
import java.util.Arrays;

/** Console utility class, not a public API. */
class ConsoleUtils {

  private static ConsoleUtils ourInstance = new ConsoleUtils();

  /**
   * Returns a singleton instance of this class.
   *
   * @return a ConsoleUtils object
   */
  static ConsoleUtils getInstance() {
    return ourInstance;
  }

  private ConsoleUtils() {}

  /**
   * Prompts the user to enter a "yes/no" response to a question on the command-line. The question
   * will be repeated if the user's response does not start with either 'y' or 'n'.
   *
   * @param prompt a yes/no-question to ask the user
   * @return {@code true} if the user entered a response starting with 'y'; {@code false} if the
   *     response started with 'n' (case-insensitive)
   */
  boolean promptForConfirmation(String prompt) {
    Console console = System.console();
    Boolean confirm = null;
    while (confirm == null) {
      String response = console.readLine(prompt + " (y/n) ");
      if (response.toLowerCase().startsWith("y")) {
        confirm = true;
      } else if (response.toLowerCase().startsWith("n")) {
        confirm = false;
      }
    }
    return confirm;
  }

  /**
   * Reads a new password from a parameter or the console. If the password provided as a parameter
   * is non-empty and valid, it will be returned. If not, the user will be prompted to enter a new
   * password in the console. The prompt will be repeated if the length of the provided password is
   * shorter than a specified minimum. After a valid password has been entered, the user must repeat
   * it to guard against accidental typos.
   *
   * @param password A chosen password (can be null)
   * @param prompt a message to display to the user if a new password must be entered in the console
   * @param minLength a required minimum length for the password
   * @return A character array containing the new password
   * @throws IllegalArgumentException if no valid password could be returned
   */
  char[] readNewPassword(String password, String prompt, int minLength)
      throws IllegalArgumentException {
    if (password != null) {
      if (password.length() >= minLength) return password.toCharArray();
      else System.out.println("Password is too short: minimum length is " + minLength);
    }
    while (true) {
      char[] newPassword = System.console().readPassword(prompt);
      if (newPassword.length >= minLength) {
        char[] confirmPassword = System.console().readPassword("Confirm password: ");
        if (Arrays.equals(newPassword, confirmPassword)) return newPassword;
        else throw new IllegalArgumentException("Passwords are not identical!");
      } else {
        System.out.println("Password is too short: minimum length is " + minLength);
      }
    }
  }
}
