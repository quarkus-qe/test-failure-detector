package io.quarkus.qe.test.failure.detector.cli;

import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReport;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReporter;
import jakarta.inject.Inject;

public class ProcessTestFailuresCommand {

    @Inject
    FailuresAnalyzer failuresAnalyzer;

    @Inject
    FailuresFinder failuresFinder;

    @Inject
    RootCauseReporter rootCauseReporter;

    private void execute() {
        RootCauseReportBuilder rootCauseReportBuilder = rootCauseReporter.builder();

        for (Failure failure : failuresFinder.find()) {
            RootCause rootCause = failuresAnalyzer.analyze(failure);
            rootCauseReportBuilder.addFailureRootCause(failure, rootCause);
        }

        RootCauseReport rootCauseReport = rootCauseReportBuilder.build();
        rootCauseReporter.report(rootCauseReport);
    }

}
