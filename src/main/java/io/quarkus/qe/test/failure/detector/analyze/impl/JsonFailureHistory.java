package io.quarkus.qe.test.failure.detector.analyze.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory;
import io.quarkus.qe.test.failure.detector.configuration.AppConfig;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JSON file-based implementation of failure history storage.
 * Stores failures in ~/.test-failure-detector/failure-history.json
 */
@ApplicationScoped
class JsonFailureHistory implements FailureHistory {

    @Inject
    Logger logger;

    private final ObjectMapper objectMapper;
    private String historyFile = "failure-history.json";

    JsonFailureHistory() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    void loadConfig(@Observes AppConfig appConfig) {
        historyFile = appConfig.historyFilePath();
    }

    @Override
    public HistoryData load() {
        Path historyPath = getHistoryPath();

        if (!Files.exists(historyPath)) {
            logger.info("No previous failure history found, starting fresh");
            return HistoryData.empty();
        }

        try {
            // Check if file is empty (0 bytes)
            if (Files.size(historyPath) == 0) {
                logger.info("History file exists but is empty, starting fresh");
                return HistoryData.empty();
            }

            logger.info("Loading failure history from: " + historyPath);
            return objectMapper.readValue(historyPath.toFile(), HistoryData.class);
        } catch (IOException e) {
            logger.error("Failed to load failure history: " + e.getMessage());
            logger.error("Starting with empty history");
            return HistoryData.empty();
        }
    }

    @Override
    public void save(HistoryData history) {
        Path historyPath = getHistoryPath();

        try {
            // Ensure directory exists
            Files.createDirectories(historyPath.getParent());

            logger.info("Saving failure history to: " + historyPath);
            objectMapper.writeValue(historyPath.toFile(), history);
            logger.info("Saved " + history.failures().size() + " tracked failures, " +
                    history.testedCommits().size() + " tested commits");
        } catch (IOException e) {
            logger.error("Failed to save failure history: " + e.getMessage());
            throw new RuntimeException("Failed to save failure history", e);
        }
    }

    private Path getHistoryPath() {
        return Paths.get(".", historyFile);
    }
}
