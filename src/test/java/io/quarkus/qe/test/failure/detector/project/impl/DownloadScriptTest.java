package io.quarkus.qe.test.failure.detector.project.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the download-github-artifacts.sh script.
 * This test verifies the script can parse URLs and execute correctly.
 */
class DownloadScriptTest {

    @TempDir
    Path tempDir;

    @Test
    void testScriptWithWorkflowUrl() throws Exception {
        Path scriptPath = getScriptPath();

        String workflowUrl = "https://github.com/quarkus-qe/mock-quarkus-test-suite/actions/workflows/daily.yaml";

        ProcessBuilder pb = new ProcessBuilder(
                scriptPath.toString(),
                workflowUrl,
                tempDir.toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println(line);
            }
        }

        String outputStr = output.toString();

        assertTrue(outputStr.contains("Repository: quarkus-qe/mock-quarkus-test-suite"),
                "Should extract repository correctly: " + outputStr);

        assertTrue(outputStr.contains("Workflow file: daily.yaml"),
                "Should extract workflow file correctly: " + outputStr);
    }

    @Test
    void testScriptWithRunUrl() throws Exception {
        Path scriptPath = getScriptPath();

        String runUrl = "https://github.com/quarkus-qe/mock-quarkus-test-suite/actions/runs/20946183562";

        ProcessBuilder pb = new ProcessBuilder(
                scriptPath.toString(),
                runUrl,
                tempDir.toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println(line);
            }
        }

        String outputStr = output.toString();

        assertTrue(outputStr.contains("Repository: quarkus-qe/mock-quarkus-test-suite"),
                "Should extract repository correctly");

        assertTrue(outputStr.contains("Run ID: 20946183562"),
                "Should extract run ID correctly");
    }

    private static Path getScriptPath() {
        // Get the script from resources
        Path resourcePath = Path.of("src/main/resources/download-github-artifacts.sh");

        assertTrue(Files.exists(resourcePath), "Script should exist at: " + resourcePath);
        assertTrue(Files.isExecutable(resourcePath), "Script should be executable");

        return resourcePath;
    }
}
