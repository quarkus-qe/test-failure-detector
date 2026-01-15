package io.quarkus.qe.test.failure.detector.analyze;

import io.quarkus.qe.test.failure.detector.find.Failure;

public interface FailuresAnalyzer {

    RootCause analyze(Failure failure);

}
