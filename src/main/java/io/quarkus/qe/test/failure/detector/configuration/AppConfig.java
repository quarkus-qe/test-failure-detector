package io.quarkus.qe.test.failure.detector.configuration;

import java.time.Instant;

public record AppConfig(int lookbackDays, Instant from, String historyFilePath, String outputFilePath, BisectStrategy bisectStrategy) {

    public enum BisectStrategy {
        /** Binary search through commits (faster, default) */
        BINARY,
        /** Linear search from oldest to newest (slower, more predictable) */
        LINEAR
    }
}
