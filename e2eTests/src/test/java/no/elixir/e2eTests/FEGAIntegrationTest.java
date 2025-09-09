package no.elixir.e2eTests;

import no.elixir.e2eTests.core.BaseE2ETest;
import no.elixir.e2eTests.features.*;
import no.elixir.e2eTests.utils.CommonUtils;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FEGAIntegrationTest {

  @BeforeAll
  static void setup() throws Exception {
    BaseE2ETest.setupTestEnvironment();
  }

  @AfterAll
  static void cleanup() {
    BaseE2ETest.cleanupTestEnvironment();
  }

  @Test
  @Order(1)
  void UploadTest() throws Exception {
    UploadTest.uploadThroughProxy();
    // Wait for triggers to be set up at CEGA.
    // Not really needed if using local CEGA container.
    CommonUtils.waitForProcessing(5000);
  }

  @Test
  @Order(2)
  void IngestTest() throws Exception {
    IngestTest.publishIngestionMessageToCEGA();
    // Wait for the LEGA ingest and verify services
    // to complete and update DB.
    CommonUtils.waitForProcessing(5000);
  }

  @Test
  @Order(3)
  void AccessionTest() throws Exception {
    AccessionTest.publishAccessionMessageOnBehalfOfCEGAToLocalEGA();
    // Wait for LEGA finalize service to complete and update DB.
    CommonUtils.waitForProcessing(5000);
  }

  @Test
  @Order(4)
  void FinalizeTest() throws Exception {
    FinalizeTest.verifyAfterFinalizeAndLookUpAccessionID();
  }

  @Test
  @Order(5)
  void MappingTest() throws Exception {
    MappingTest.triggerMappingMessageFromCEGA();
    // Wait for LEGA mapper service to store mapping
    CommonUtils.waitForProcessing(1000);
  }

  @Test
  @Order(6)
  void ReleaseTest() throws Exception {
    ReleaseTest.triggerReleaseMessageFromCEGA();
    // Wait for LEGA mapper service to update dataset status
    CommonUtils.waitForProcessing(1000);
  }

  @Test
  @Order(7)
  void DownloadTest() throws Exception {
    DownloadTest.downloadDatasetAndVerifyResults();
  }
}
