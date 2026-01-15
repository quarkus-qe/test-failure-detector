package io.quarkus.qe.test.failure.detector.report.impl;

import io.quarkus.qe.test.failure.detector.report.RootCauseReport;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReporter;
import jakarta.inject.Singleton;

@Singleton
final class RootCauseReporterImpl implements RootCauseReporter {

    @Override
    public void report(RootCauseReport rootCauseReport) {
        // report
        //  - medium: email, GitHub comment, STDOUT
    }

    @Override
    public RootCauseReportBuilder builder() {
        // FIXME: impl. me!
        return null;
    }
}
