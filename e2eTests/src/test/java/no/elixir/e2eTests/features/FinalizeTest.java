package no.elixir.e2eTests.features;

import no.elixir.e2eTests.core.State;
import no.elixir.e2eTests.utils.CertificateUtils;

import java.io.File;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.fail;

public class FinalizeTest {

    /**
     * Verify that everything is ok so far.
     */
    public static void verifyAfterFinalizeAndLookUpAccessionID() throws Exception {
        State.log.info("Starting verification of state after finalize step...");
        File rootCA = CertificateUtils.getCertificateFile("rootCA.pem");
        File client = CertificateUtils.getCertificateFile("client.pem");
        File clientKey = CertificateUtils.getCertificateFile("client-key.der");
        String url =
                String.format(
                        "jdbc:postgresql://%s:%s/%s",
                        State.env.getSdaDbHost(), State.env.getSdaDbPort(), State.env.getSdaDbDatabaseName());
        Properties props = new Properties();
        props.setProperty("user", State.env.getSdaDbUsername());
        props.setProperty("password", State.env.getSdaDbPassword());
        props.setProperty("application_name", "LocalEGA");
        props.setProperty("sslmode", "verify-full");
        props.setProperty("sslcert", client.getAbsolutePath());
        props.setProperty("sslkey", clientKey.getAbsolutePath());
        props.setProperty("sslrootcert", rootCA.getAbsolutePath());
        java.sql.Connection conn = DriverManager.getConnection(url, props);
        String sql =
                "select archive_path,stable_id from local_ega.files where status = 'READY' AND inbox_path = ?";
        PreparedStatement statement = conn.prepareStatement(sql);
        statement.setString(1, "/p11-dummy@elixir-europe.org/files/" + State.encFile.getName());
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.wasNull() || !resultSet.next()) {
            fail("Verification failed");
        }
        State.archivePath = resultSet.getString(1);
        State.stableId = resultSet.getString(2);
        State.log.info("Stable ID: {}", State.stableId);
        State.log.info("Archive path: {}", State.archivePath);
        State.log.info("Verification completed successfully");
    }

}
