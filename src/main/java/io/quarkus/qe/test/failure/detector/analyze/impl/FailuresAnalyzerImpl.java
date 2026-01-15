package io.quarkus.qe.test.failure.detector.analyze.impl;

import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.find.Failure;
import jakarta.inject.Singleton;

@Singleton
final class FailuresAnalyzerImpl implements FailuresAnalyzer {

    @Override
    public RootCause analyze(Failure failure) {
        // identify root cause for failures
        //  - ignore known failures, probably use GH artifacts to keep information
        //  - ignore the same failures that previous "failure" already analyzed, e.g. in one test module the reason is most likely same
        //  - Changes: types - internal (QE project), external (upstream, use git bisect)
        return null;
    }

}
