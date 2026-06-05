package no.elixir.e2eTests.features;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import no.elixir.e2eTests.core.E2EState;
import no.elixir.e2eTests.utils.CommonUtils;

public class InboxCleanupTest {

  // The inbox volume is mounted read-only into the e2e-tests container at the same
  // path the SDA services see it.
  private static final String INBOX_ROOT = "/ega/inbox";

  /**
   * After mapping a file to a dataset, the mapper removes the uploaded file from the inbox. Nothing
   * in the DB records that removal, so without this check a mapper that silently fails to delete
   * (e.g. wrong path or a permission error) would still let the suite pass. Poll until the file is
   * gone and fail if it never is.
   */
  public static void verifyInboxFileRemovedAfterMapping() {
    String tsdProject = E2EState.env.getTsdProject();
    // The physical inbox dir is <projectCode>-<LS-AAI subject> (e.g. p11-dummy@elixir-europe.org).
    // It matches what the mapper deletes only because the mq-interceptor maps the CEGA user to
    // exactly this LS-AAI subject, which SDA reconstructs via ResolveInboxPath. See
    // FEGA-Norway#820.
    String subject = E2EState.env.getLsaaiSubject();
    File inboxFile =
        new File(
            INBOX_ROOT,
            "%s-%s/files/%s".formatted(tsdProject, subject, E2EState.encFile.getName()));
    E2EState.log.info("Verifying mapper removed the inbox file: {}", inboxFile.getAbsolutePath());

    int attempts = 20;
    while (inboxFile.exists() && attempts-- > 0) {
      CommonUtils.waitForProcessing(1000);
    }

    assertFalse(
        inboxFile.exists(),
        "Mapper did not remove the uploaded file from the inbox: " + inboxFile.getAbsolutePath());
    E2EState.log.info("Inbox cleanup verified: mapper removed the file");
  }
}
