package io.quarkus.qe.test.failure.detector.configuration;

import io.quarkus.qe.test.failure.detector.project.ProjectSource;

import java.time.Instant;

public record AppConfig(int lookbackDays, Instant from, String historyFilePath, String outputFilePath,
                        BisectStrategy bisectStrategy, String testSuiteRepoUrl,
                        ProjectSource projectSource, String projectSourceArgument) {

    public enum BisectStrategy {
        /** Binary search through commits (faster, default) */
        BINARY,
        /** Linear search from oldest to newest (slower, more predictable) */
        LINEAR
    }
}
