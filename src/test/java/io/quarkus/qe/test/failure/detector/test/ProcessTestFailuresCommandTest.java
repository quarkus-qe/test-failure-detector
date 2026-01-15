package io.quarkus.qe.test.failure.detector.test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class ProcessTestFailuresCommandTest {

    /**
     * In this directory (the current directory), there are test failures in test resources.
     */
    @Launch(value = {
            "LOCAL_DIRECTORY", "src/test/resources/failsafe-reports/no-failures"
    })
    @Test
    void testNoTestFailures(LaunchResult result) {
        assertTrue(result.getOutput().contains("Looking for test failures"), result.getOutput());
        assertTrue(result.getOutput().contains("Found failsafe summary file src/test/resources/failsafe-reports/"
                + "no-failures/target/failsafe-reports/failsafe-summary.xml with no failures"), result.getOutput());
    }

    // FIXME: remote following
//    @Test
//    @Launch("World")
//    public void testLaunchCommand(LaunchResult result) {
//        Assertions.assertTrue(result.getOutput().contains("Hello World"));
//    }
//
//    @Test
//    @Launch(value = {}, exitCode = 1)
//    public void testLaunchCommandFailed() {
//    }
//
//    @Test
//    public void testManualLaunch(QuarkusMainLauncher launcher) {
//        LaunchResult result = launcher.launch("Everyone");
//        Assertions.assertEquals(0, result.exitCode());
//        Assertions.assertTrue(result.getOutput().contains("Hello Everyone"));
//    }
}
