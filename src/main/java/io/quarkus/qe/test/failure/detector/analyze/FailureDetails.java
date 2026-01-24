package io.quarkus.qe.test.failure.detector.analyze;

import io.quarkus.qe.test.failure.detector.find.Failure;

/**
 * Represents an individual test failure within a root cause.
 * Multiple FailureDetails can be grouped under a single AnalyzedRootCause.
 */
public record FailureDetails(
        String testClassName,
        String testMethodName,
        String failureMessage,
        String throwableClass,
        Failure.FailureType failureType,
        boolean isPrimaryFailure) {

    /**
     * Create FailureDetails from a Failure object.
     *
     * @param failure the failure to convert
     * @param isPrimary whether this is the primary (first) failure in a group
     * @return FailureDetails instance
     */
    public static FailureDetails from(Failure failure, boolean isPrimary) {
        return new FailureDetails(
                failure.testClassName(),
                failure.testMethodName(),
                failure.failureMessage(),
                failure.throwableClass(),
                failure.failureType(),
                isPrimary
        );
    }
}
