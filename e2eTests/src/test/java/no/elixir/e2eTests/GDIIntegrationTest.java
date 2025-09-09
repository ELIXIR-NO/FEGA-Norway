package no.elixir.e2eTests;

import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GDIIntegrationTest {

  @BeforeAll
  static void setup() throws Exception {}

  @AfterAll
  static void cleanup() {}

  @Test
  @Order(1)
  void UploadTest() throws Exception {}

  @Test
  @Order(2)
  void IngestTest() throws Exception {}

  @Test
  @Order(3)
  void AccessionTest() throws Exception {}

  @Test
  @Order(4)
  void FinalizeTest() throws Exception {}

  @Test
  @Order(5)
  void MappingTest() throws Exception {}

  @Test
  @Order(6)
  void ReleaseTest() throws Exception {}

  @Test
  @Order(7)
  void DownloadTest() throws Exception {}
}
