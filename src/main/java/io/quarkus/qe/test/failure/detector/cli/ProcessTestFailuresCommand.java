package io.quarkus.qe.test.failure.detector.cli;

import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.qe.test.failure.detector.output.Data;
import io.quarkus.qe.test.failure.detector.output.OutputChannel;
import io.quarkus.qe.test.failure.detector.project.ProjectSource;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilderProvider;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command
public class ProcessTestFailuresCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Parameters(arity = "1", paramLabel = "PROJECT_SOURCE", description = """
            Source of a project with test results.
            By default, this command looks for the failures in the current directory with the 'LOCAL_DIRECTORY' source.
            If the 'GITHUB_ACTION_ARTIFACTS' project source is selected, you may also specify the specific workflow.
            """, defaultValue = "LOCAL_DIRECTORY")
    ProjectSource projectSource;

    @CommandLine.Parameters(arity = "1", paramLabel = "PROJECT_SOURCE_ARGUMENT", description = """
            Arguments passed to the project source.
            For the 'GITHUB_ACTION_ARTIFACTS' project source, this is a link to the project workflow.
            """, defaultValue = "https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml")
    String projectSourceArgument;

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

        Path projectWithPossibleTestFailures = projectSource.getTestedProjectDirectory(projectSourceArgument);

        Data data = failuresFinder
                .find(projectWithPossibleTestFailures)
                .stream()
                .map(failuresAnalyzer::analyze)
                .reduce(reportBuilderProvider.builder(), RootCauseReportBuilder::addRootCause, (b, _) -> b)
                .build();

        outputChannel.process(data);
    }

}
