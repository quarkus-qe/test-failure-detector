package io.quarkus.qe.test.failure.detector.find.impl;

import io.quarkus.qe.test.failure.detector.cli.ConsoleLogger;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;

@Singleton
final class FailuresFinderImpl implements FailuresFinder {

    @Inject
    ConsoleLogger consoleLogger;

    @Override
    public Collection<Failure> find() {
        consoleLogger.info("Looking for test failures");
        // find failures
        //  - source: e.g. GitHub
        //  - output: Failure (dir, test, configuration [JDK version, mode - JVM/DEV/native/OCP], arguments [like db images, native builder etc.])
        return List.of();
    }
}
