package io.quarkus.qe.test.failure.detector.report.impl;

import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilderProvider;
import jakarta.inject.Singleton;

@Singleton
final class RootCauseReportBuilderProviderImpl implements RootCauseReportBuilderProvider {

    @Override
    public RootCauseReportBuilder builder() {
        return new RootCauseReportBuilderImpl();
    }
}
