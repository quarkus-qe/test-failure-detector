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

    record UpstreamChange(String gitCommitSHA, String prNumber, String gitCommitMessage, FailureReason failureReason) {
    }

    /**
     * Reason why bisect couldn't identify the failure-introducing commit.
     */
    enum FailureReason {
        /** Successfully found the commit */
        FOUND,
        /** Quarkus build failed during bisect */
        BUILD_FAILED,
        /** Failure exists at oldest commit in lookback range (introduced before lookback) */
        OLDEST_COMMIT_FAILED,
        /** Test passes on all commits during bisect (flaky or environmental) */
        CANNOT_REPRODUCE
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
