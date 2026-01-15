package io.quarkus.qe.test.failure.detector.find.impl;

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

    @Override
    public Collection<Failure> find(Path testedProjectDir) {
        logger.info("Looking for test failures in directory: " + testedProjectDir.toAbsolutePath());
        // find failures
        //  - project with test results: e.g. from GitHub
        //    - download the project with test results
        //    - parse failsafe reports
        //    - transform it into Failure
        //  - output: Failure (dir, test, configuration [JDK version, mode - JVM/DEV/native/OCP], arguments [like db images, native builder etc.])
        return List.of();
    }
}
