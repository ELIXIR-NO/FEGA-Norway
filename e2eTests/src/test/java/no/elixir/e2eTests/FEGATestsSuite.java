package no.elixir.e2eTests;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("All End-to-End Tests in Specified Order")
// The @SelectClasses annotation specifies the test classes to include in the suite
// and, importantly, the order in which they should be executed.
// The JUnit Platform will run these classes sequentially in the order listed here.
@SelectClasses({
        UploadTest.class,
        IngestTest.class,
        AccessionTest.class,
        FinalizeTest.class,
        MappingTest.class,
        ReleaseTest.class,
        DownloadHackTest.class,
})
public class FEGATestsSuite {

}
