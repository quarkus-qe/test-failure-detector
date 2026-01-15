package io.quarkus.qe.test.failure.detector.report.impl;

import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.report.RootCauseReport;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;

final class RootCauseReportBuilderImpl implements RootCauseReportBuilder {

    @Override
    public RootCauseReportBuilder addRootCause(RootCause rootCause) {
        // FIXME: impl. me!
        return this;
    }

    @Override
    public RootCauseReport build() {
        return new RootCauseReport() {
        };
    }
}
