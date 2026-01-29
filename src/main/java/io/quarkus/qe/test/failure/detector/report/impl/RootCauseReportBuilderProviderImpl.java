package io.quarkus.qe.test.failure.detector.report.impl;

import io.quarkus.qe.test.failure.detector.configuration.AppConfig;
import io.quarkus.qe.test.failure.detector.project.ProjectSource;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilderProvider;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

@Singleton
final class RootCauseReportBuilderProviderImpl implements RootCauseReportBuilderProvider {

    private ProjectSource projectSource;
    private String projectSourceArgument;

    void updateConfig(@Observes AppConfig appConfig) {
        this.projectSource = appConfig.projectSource();
        this.projectSourceArgument = appConfig.projectSourceArgument();
    }

    @Override
    public RootCauseReportBuilder builder() {
        return new RootCauseReportBuilderImpl(projectSource, projectSourceArgument);
    }
}
