package io.quarkus.qe.test.failure.detector.analyze;

import io.quarkus.qe.test.failure.detector.find.Failure;

/**
 * Implementations of this interface must find what has changed upstream (in Quarkus main project).
 */
public interface UpstreamChangeFinder {

    /**
     * Find the upstream change that caused the current failure.
     *
     * @return upstream change or null
     */
    RootCause.UpstreamChange findUpstreamChange(Failure failure);

}
