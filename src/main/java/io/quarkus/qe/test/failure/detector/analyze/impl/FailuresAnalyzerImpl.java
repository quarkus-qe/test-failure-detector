package io.quarkus.qe.test.failure.detector.analyze.impl;

import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
final class FailuresAnalyzerImpl implements FailuresAnalyzer {

    @Inject
    Logger logger;

    @Override
    public RootCause analyze(Failure failure) {
        logger.info("Analyzing test failure: " + failure);
        // identify root cause for failures
        //  - ignore known failures, probably use GH artifacts to keep information
        //  - ignore the same failures that previous "failure" already analyzed, e.g. in one test module the reason is most likely same
        //  - Changes: types - internal (QE project), external (upstream, use git bisect)
        return null;
    }

}
