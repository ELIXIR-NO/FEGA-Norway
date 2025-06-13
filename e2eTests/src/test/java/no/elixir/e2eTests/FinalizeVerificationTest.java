package no.elixir.e2eTests;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.fail;

public class FinalizeVerificationTest extends BaseE2ETest {

    @Test
    public void testVerifyAfterFinalizeAndLookUpAccessionID() throws Exception {
        setupTestEnvironment();
        try {
            // Verify that everything is ok so far
            verifyAfterFinalizeAndLookUpAccessionID();
        } finally {
            cleanupTestEnvironment();
        }
    }

    private void verifyAfterFinalizeAndLookUpAccessionID() throws Exception {
        log.info("Starting verification of state after finalize step...");
        File rootCA = getCertificateFile("rootCA.pem");
        File client = getCertificateFile("client.pem");
        File clientKey = getCertificateFile("client-key.der");
        String url =
                String.format(
                        "jdbc:postgresql://%s:%s/%s",
                        env.getSdaDbHost(), env.getSdaDbPort(), env.getSdaDbDatabaseName());
        Properties props = new Properties();
        props.setProperty("user", env.getSdaDbUsername());
        props.setProperty("password", env.getSdaDbPassword());
        props.setProperty("application_name", "LocalEGA");
        props.setProperty("sslmode", "verify-full");
        props.setProperty("sslcert", client.getAbsolutePath());
        props.setProperty("sslkey", clientKey.getAbsolutePath());
        props.setProperty("sslrootcert", rootCA.getAbsolutePath());
        java.sql.Connection conn = DriverManager.getConnection(url, props);
        String sql =
                "select archive_path,stable_id from local_ega.files where status = 'READY' AND inbox_path = ?";
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, "/p11-dummy@elixir-europe.org/files/" + encFile.getName());
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.wasNull() || !resultSet.next()) {
            fail("Verification failed");
        }
        archivePath = resultSet.getString(1);
        stableId = resultSet.getString(2);
        log.info("Stable ID: {}", stableId);
        log.info("Archive path: {}", archivePath);
        log.info("Verification completed successfully");
    }

}
