package io.quarkus.qe.test.failure.detector.test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
    })
    @Test
    void testGitHubArtifactFailures(LaunchResult result) {
        assertTrue(result.getOutput().contains("Looking for test failures"), result.getOutput());
        assertTrue(result.getOutput().contains("Found 0 errors and 1 failures"), result.getOutput());
    }

    @Launch(value = {
            "LOCAL_DIRECTORY", "src/test/resources/failsafe-reports/single-module",
            "--output-file", "target/test-output-report.txt"
    })
    @Test
    void testOutputFileIsCreated(LaunchResult result) throws Exception {
        // Verify the command ran successfully
        assertTrue(result.getOutput().contains("Looking for test failures"), result.getOutput());
        assertTrue(result.getOutput().contains("Found 0 errors and 1 failures"), result.getOutput());

        // Verify the output file was created
        assertTrue(result.getOutput().contains("Report saved to:"), result.getOutput());

        Path outputFile = Path.of("target/test-output-report.txt");
        assertTrue(Files.exists(outputFile), "Output file should exist");

        // Read and verify the file content
        String fileContent = Files.readString(outputFile);
        assertTrue(fileContent.contains("Test Failure Analysis Report"),
                "File should contain the report header");
        assertTrue(fileContent.contains("Root Cause"),
                "File should contain root cause information");

        assertTrue(fileContent.contains("GreetingResourceIT"),
                "GreetingResourceIT failure should had been detected");

        // Clean up
        Files.deleteIfExists(outputFile);
    }
}
