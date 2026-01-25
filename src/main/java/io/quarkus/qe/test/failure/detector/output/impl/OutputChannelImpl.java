package io.quarkus.qe.test.failure.detector.output.impl;

import io.quarkus.qe.test.failure.detector.configuration.AppConfig;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import io.quarkus.qe.test.failure.detector.output.Data;
import io.quarkus.qe.test.failure.detector.output.OutputChannel;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Singleton
final class OutputChannelImpl implements OutputChannel {

    private final Logger logger;
    private String outputFilePath;

    OutputChannelImpl(Logger logger) {
        this.logger = logger;
    }

    void updateConfiguration(@Observes AppConfig appConfig) {
        this.outputFilePath = appConfig.outputFilePath();
    }

    @Override
    public void process(Data data) {
        try (var reader = data.reader()) {
            String output = reader.readAllAsString();

            // Always log to stdout
            logger.info(output);

            // Also write to file if configured
            if (outputFilePath != null && !outputFilePath.isBlank()) {
                writeToFile(output);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeToFile(String content) {
        try {
            Path outputPath = Paths.get(outputFilePath);

            // Create parent directories if they don't exist
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            Files.writeString(outputPath, content);
            logger.info("Report saved to: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write report to file: " + outputFilePath);
            throw new RuntimeException("Failed to write report to file: " + outputFilePath, e);
        }
    }

}
