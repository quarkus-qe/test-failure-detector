package io.quarkus.qe.test.failure.detector.analyze;

import java.util.List;

public interface RootCause {

    String identifier();

    String modulePath();

    String summary();

    ConfidenceLevel confidence();

    List<FailureDetails> failures();

    AnalysisMetadata metadata();

    UpstreamChange upstreamChange();

    record UpstreamChange(String gitCommitSHA, String prNumber, String gitCommitMessage) {
    }

    /**
     * Confidence level of the root cause analysis.
     */
    enum ConfidenceLevel {
        /** High confidence - failures from the same test class */
        HIGH,
        /** Medium confidence - failures from the same module */
        MEDIUM,
        /** Low confidence - guessed or inferred relationship */
        LOW
    }
}
