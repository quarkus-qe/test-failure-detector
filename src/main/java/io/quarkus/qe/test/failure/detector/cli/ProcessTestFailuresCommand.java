package io.quarkus.qe.test.failure.detector.cli;

import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.qe.test.failure.detector.output.OutputChannel;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilderProvider;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.nio.file.Path;

@CommandLine.Command
public class ProcessTestFailuresCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Parameters(arity = "1", paramLabel = "TESTED_RPOJECT_DIR", description = "Root directory of a project with test results", defaultValue = ".")
    Path testedProjectDir;

    @Inject
    FailuresAnalyzer failuresAnalyzer;

    @Inject
    FailuresFinder failuresFinder;

    @Inject
    RootCauseReportBuilderProvider reportBuilderProvider;

    @Inject
    OutputChannel outputChannel;

    @Inject
    ConsoleLogger consoleLogger;

    @Override
    public void run() {
        consoleLogger.setWriters(spec.commandLine().getOut(), spec.commandLine().getErr());

        outputChannel.process(failuresFinder
                .find(testedProjectDir)
                .stream()
                .map(failuresAnalyzer::analyze)
                .reduce(reportBuilderProvider.builder(), RootCauseReportBuilder::addRootCause, (b, _) -> b)
                .build());
    }

    @Produces
    PrintWriter stdOutPrinter() {
        return spec.commandLine().getOut();
    }

}
