package io.quarkus.qe.test.failure.detector.analyze;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tracks test failures across multiple runs to identify new vs. existing failures.
 */
public interface FailureHistory {

    /**
     * Load the failure history from storage.
     *
     * @return the loaded history
     */
    HistoryData load();

    /**
     * Save the failure history to storage.
     *
     * @param history the history to save
     */
    void save(HistoryData history);

    /**
     * Data representing failure history across runs.
     */
    @RegisterForReflection
    record HistoryData(
            Instant lastRun,
            String quarkusCommit,
            List<TrackedFailure> failures,
            List<String> testedCommits) {

        public HistoryData {
            failures = List.copyOf(failures);
            testedCommits = List.copyOf(testedCommits);
        }

        /**
         * Create an empty history.
         */
        public static HistoryData empty() {
            return new HistoryData(Instant.now(), null, List.of(), List.of());
        }

        /**
         * Find a tracked failure by test class and method.
         */
        public Optional<TrackedFailure> findFailure(String testClassName, String testMethodName) {
            return failures.stream()
                    .filter(f -> f.testClassName().equals(testClassName) &&
                            f.testMethodName().equals(testMethodName))
                    .findFirst();
        }

        /**
         * Check if a commit has been tested.
         */
        public boolean isCommitTested(String commit) {
            return testedCommits.contains(commit);
        }
    }

    /**
     * Represents a tracked failure across multiple runs.
     */
    @RegisterForReflection
    record TrackedFailure(
            String testClassName,
            String testMethodName,
            String modulePath,
            Instant firstSeen,
            Instant lastSeen,
            FailureStatus status,
            String upstreamCommit,
            String upstreamPullRequest) {

        /**
         * Status of a tracked failure.
         */
        @RegisterForReflection
        public enum FailureStatus {
            /** Failure appeared for the first time in this run */
            NEW,
            /** Failure existed in previous run and still exists */
            EXISTING,
            /** Failure existed before but is now resolved */
            RESOLVED
        }

        /**
         * Create a new tracked failure.
         */
        public static TrackedFailure createNew(String testClassName, String testMethodName, String modulePath) {
            Instant now = Instant.now();
            return new TrackedFailure(
                    testClassName,
                    testMethodName,
                    modulePath,
                    now,
                    now,
                    FailureStatus.NEW,
                    null,
                    null
            );
        }

        /**
         * Mark this failure as seen again (existing).
         */
        public TrackedFailure markSeen() {
            return new TrackedFailure(
                    testClassName,
                    testMethodName,
                    modulePath,
                    firstSeen,
                    Instant.now(),
                    FailureStatus.EXISTING,
                    upstreamCommit,
                    upstreamPullRequest
            );
        }

        /**
         * Update with upstream commit information.
         */
        public TrackedFailure withUpstreamCommit(String commit, String pullRequest) {
            return new TrackedFailure(
                    testClassName,
                    testMethodName,
                    modulePath,
                    firstSeen,
                    lastSeen,
                    status,
                    commit,
                    pullRequest
            );
        }
    }
}
