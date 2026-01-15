package io.quarkus.qe.test.failure.detector.find.impl;

import io.quarkus.arc.All;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

@Singleton
final class FailuresFinderImpl implements FailuresFinder {

    @Inject
    Logger logger;

    @All
    @Inject
    List<FailuresFinderStrategy> failuresFinderStrategies;

    @Override
    public Collection<Failure> find(Path testedProjectDir) {
        logger.info("Looking for test failures in directory: " + testedProjectDir.toAbsolutePath());
        return failuresFinderStrategies.stream()
                .map(strategy -> strategy.find(testedProjectDir))
                .flatMap(Collection::stream)
                .toList();
    }
}
