package io.quarkus.qe.test.failure.detector.analyze.impl;

import io.quarkus.qe.test.failure.detector.analyze.AnalysisMetadata;
import io.quarkus.qe.test.failure.detector.analyze.FailureDetails;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a root cause analysis result for test failures.
 * Multiple failures can be grouped under a single root cause when they likely share the same underlying issue.
 */
record AnalyzedRootCause(
        String identifier,
        String modulePath,
        String summary,
        RootCause.ConfidenceLevel confidence,
        List<FailureDetails> failures,
        AnalysisMetadata metadata,
        UpstreamChange upstreamChange) implements RootCause {

    /**
     * Compact constructor to ensure immutable list.
     */
    AnalyzedRootCause {
        failures = List.copyOf(failures);
    }

    /**
     * Create a new root cause from a primary failure.
     *
     * @param identifier unique identifier for this root cause
     * @param modulePath absolute path to the module
     * @param summary human-readable summary
     * @param confidence confidence level
     * @param primaryFailure the primary failure details
     * @param metadata analysis metadata
     * @param upstreamChange what has changed upstream
     * @return new AnalyzedRootCause instance
     */
    static AnalyzedRootCause create(
            String identifier,
            String modulePath,
            String summary,
            ConfidenceLevel confidence,
            FailureDetails primaryFailure,
            AnalysisMetadata metadata,
            UpstreamChange upstreamChange) {
        return new AnalyzedRootCause(
                identifier,
                modulePath,
                summary,
                confidence,
                List.of(primaryFailure),
                metadata,
                upstreamChange
        );
    }

    /**
     * Add a deduplicated failure to this root cause.
     *
     * @param failure the failure to add
     * @return new AnalyzedRootCause with the added failure
     */
    AnalyzedRootCause addFailure(FailureDetails failure) {
        List<FailureDetails> updatedFailures = new ArrayList<>(this.failures);
        updatedFailures.add(failure);

        return new AnalyzedRootCause(
                this.identifier,
                this.modulePath,
                this.summary,
                this.confidence,
                updatedFailures,
                this.metadata.addDedupedFailure(),
                this.upstreamChange
        );
    }

    @Override
    public String toString() {
        return "AnalyzedRootCause[" +
                "identifier=" + identifier +
                ", confidence=" + confidence +
                ", failures=" + failures.size() +
                ", upstreamChange=" + upstreamChange +
                " (" + (failures.size() - metadata.dedupedFailures()) + " primary + " +
                metadata.dedupedFailures() + " deduplicated)" +
                ']';
    }
}
