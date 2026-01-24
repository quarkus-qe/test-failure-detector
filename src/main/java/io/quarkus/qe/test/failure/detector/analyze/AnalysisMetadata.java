package io.quarkus.qe.test.failure.detector.analyze;

import java.time.Instant;

/**
 * Metadata about the failure analysis process.
 * Tracks when the analysis was performed and deduplication statistics.
 */
public record AnalysisMetadata(
        Instant analyzedAt,
        DeduplicationStrategy strategy,
        int totalFailures,
        int dedupedFailures) {

    /**
     * Strategy used for deduplicating failures.
     */
    public enum DeduplicationStrategy {
        /** No deduplication performed - each failure analyzed individually */
        NONE,
        /** Failures grouped by test class */
        BY_CLASS,
        /** Failures grouped by module path */
        BY_MODULE
    }

    /**
     * Create metadata for a new root cause analysis.
     *
     * @param strategy the deduplication strategy used
     * @return AnalysisMetadata instance
     */
    public static AnalysisMetadata create(DeduplicationStrategy strategy) {
        return new AnalysisMetadata(Instant.now(), strategy, 1, 0);
    }

    /**
     * Update metadata when adding a deduplicated failure.
     *
     * @return updated AnalysisMetadata with incremented counts
     */
    public AnalysisMetadata addDedupedFailure() {
        return new AnalysisMetadata(
                this.analyzedAt,
                this.strategy,
                this.totalFailures + 1,
                this.dedupedFailures + 1
        );
    }
}
