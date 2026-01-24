package io.quarkus.qe.test.failure.detector.analyze;

/**
 * Implementations of this interface must find what has changed upstream (in Quarkus main project).
 */
public interface UpstreamChangeFinder {

    /**
     * @return upstream change or null
     */
    RootCause.UpstreamChange findUpstreamChange();

}
