package io.quarkus.qe.test.failure.detector.project.impl;

import io.quarkus.arc.Unremovable;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Unremovable
@Dependent
public class GitHubWorkflowProjectSource {

    @Inject
    Logger logger;

    public Path getProjectFailuresDir(String workflowLink) {
        logger.info("Downloading GitHub workflow artifacts from: " + workflowLink);

        try {
            // Create a temporary directory for artifacts
            Path tempDir = Files.createTempDirectory("github-artifacts-" + UUID.randomUUID());
            logger.info("Created temporary directory: " + tempDir);

            // Get the path to the download script
            Path scriptPath = getDownloadScriptPath();

            // Execute the download script
            ProcessBuilder pb = new ProcessBuilder(
                    scriptPath.toString(),
                    workflowLink,
                    tempDir.toString()
            );
            pb.redirectErrorStream(true);

            logger.info("Executing download script...");
            Process process = pb.start();

            // Read and log script output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("  " + line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("Failed to download artifacts. Script exit code: " + exitCode);
            }

            logger.info("Successfully downloaded and extracted artifacts to: " + tempDir);
            return tempDir;

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error downloading GitHub artifacts: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the path to the download script.
     * Extracts it from resources if needed.
     */
    private Path getDownloadScriptPath() throws IOException {
        // Try to find the script in the resources
        InputStream scriptStream = getClass().getClassLoader()
                .getResourceAsStream("download-github-artifacts.sh");

        if (scriptStream == null) {
            throw new IOException("Could not find download-github-artifacts.sh in resources");
        }

        // Create a temporary file for the script
        Path scriptPath = Files.createTempFile("download-github-artifacts", ".sh");
        Files.copy(scriptStream, scriptPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Make it executable
        scriptPath.toFile().setExecutable(true);

        return scriptPath;
    }
}
