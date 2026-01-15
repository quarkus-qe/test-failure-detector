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

    @Launch(value = {
            "LOCAL_DIRECTORY", "src/test/resources/github-artifacts/mock-artifact"
    }, exitCode = 1)
    @Test
    void testGitHubArtifactFailures(LaunchResult result) {
        assertTrue(result.getOutput().contains("Looking for test failures"), result.getOutput());
        assertTrue(result.getOutput().contains("Found 0 errors and 1 failures"), result.getOutput());
    }

}
