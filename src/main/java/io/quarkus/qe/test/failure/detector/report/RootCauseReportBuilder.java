package io.quarkus.qe.test.failure.detector.report;

import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.find.Failure;

public interface RootCauseReportBuilder {

    void addFailureRootCause(Failure failure, RootCause rootCause);

    RootCauseReport build();

}
