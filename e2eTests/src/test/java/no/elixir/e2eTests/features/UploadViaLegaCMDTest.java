package no.elixir.e2eTests.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.TokenUtils;
import org.jspecify.annotations.NonNull;

public class UploadViaLegaCMDTest {

  public static void uploadViaLegaCommander() throws Exception {
    E2EState.log.info("Uploading a file via lega-commander CLI...");

    String token = TokenUtils.generateVisaToken("upload");
    E2EState.log.info("Visa JWT token for lega-commander upload: {}", token);

    ProcessBuilder pb = getProcessBuilder(token);

    E2EState.log.info("Starting lega-commander process...");
    Process process = pb.start();

    // Redirect lega-cmd output to this process.
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append(System.lineSeparator());
        E2EState.log.info("[lega-commander] {}", line);
      }
    }

    boolean finished = process.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new AssertionError("lega-commander process timed out after 120 seconds");
    }

    int exitCode = process.exitValue();
    E2EState.log.info("lega-commander exit code: {}", exitCode);
    E2EState.log.info("lega-commander output:\n{}", output);

    assertEquals(0, exitCode, "lega-commander upload failed with output:\n" + output);
  }

  private static @NonNull ProcessBuilder getProcessBuilder(String token) {
    String instanceURL =
        String.format(
            "https://%s:%s",
            E2EState.env.getProxyHost(), E2EState.env.getProxyPort());

    ProcessBuilder pb =
        new ProcessBuilder(
            "/usr/local/bin/lega-commander",
            "upload",
            "-f",
            E2EState.encFile.getAbsolutePath());

    Map<String, String> env = pb.environment();
    env.put("LOCAL_EGA_INSTANCE_URL", instanceURL);
    env.put("CENTRAL_EGA_USERNAME", E2EState.env.getCegaAuthUsername());
    env.put("CENTRAL_EGA_PASSWORD", E2EState.env.getCegaAuthPassword());
    env.put("ELIXIR_AAI_TOKEN", token);
    env.put("LEGA_COMMANDER_TLS_SKIP_VERIFY", "true");

    pb.redirectErrorStream(true);
    return pb;
  }
}
