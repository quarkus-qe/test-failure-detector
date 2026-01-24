package io.quarkus.qe.test.failure.detector.analyze.upstream.change.finder;

import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.analyze.UpstreamChangeFinder;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class BruteForceUpstreamChangeFinder implements UpstreamChangeFinder {

    @Inject
    Logger logger;

    @Override
    public RootCause.UpstreamChange findUpstreamChange() {

        return null;
    }
}
