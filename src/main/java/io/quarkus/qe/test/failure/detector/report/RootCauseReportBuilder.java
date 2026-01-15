package io.quarkus.qe.test.failure.detector.report;

import io.quarkus.qe.test.failure.detector.analyze.RootCause;

public interface RootCauseReportBuilder {

    RootCauseReportBuilder addRootCause(RootCause rootCause);

    RootCauseReport build();

}
