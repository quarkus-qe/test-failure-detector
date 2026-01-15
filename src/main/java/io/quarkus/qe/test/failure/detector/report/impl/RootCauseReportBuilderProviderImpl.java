package io.quarkus.qe.test.failure.detector.report.impl;

import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilderProvider;
import jakarta.inject.Singleton;

@Singleton
final class RootCauseReportBuilderProviderImpl implements RootCauseReportBuilderProvider {

    // report
    //  - medium: email, GitHub comment, STDOUT

    @Override
    public RootCauseReportBuilder builder() {
        // FIXME: impl. me!
        return null;
    }
}
