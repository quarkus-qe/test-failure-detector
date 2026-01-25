package io.quarkus.qe.test.failure.detector;

import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.analyze.UpstreamChangeFinder;
import io.quarkus.qe.test.failure.detector.find.Failure;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

@Alternative
@Priority(1)
@Singleton
public class MockUpstreamChangeFinder implements UpstreamChangeFinder {

    @Override
    public RootCause.UpstreamChange findUpstreamChange(Failure failure) {
        // Return null in tests to avoid cloning repositories
        return null;
    }
}
