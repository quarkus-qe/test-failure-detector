package io.quarkus.qe.test.failure.detector.configuration;

import java.time.Instant;

public record AppConfig(int lookbackDays, Instant from, String historyFilePath) {
}
