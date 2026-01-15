package io.quarkus.qe.test.failure.detector.cli;

import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.qe.test.failure.detector.output.OutputChannel;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilderProvider;
import jakarta.inject.Inject;

public class ProcessTestFailuresCommand {

    @Inject
    FailuresAnalyzer failuresAnalyzer;

    @Inject
    FailuresFinder failuresFinder;

    @Inject
    RootCauseReportBuilderProvider reportBuilderProvider;

    @Inject
    OutputChannel outputChannel;

    private void execute() {
        outputChannel.process(failuresFinder.find()
                .stream()
                .map(failuresAnalyzer::analyze)
                .reduce(reportBuilderProvider.builder(), RootCauseReportBuilder::addRootCause, (b, _) -> b)
                .build());
    }

}
