package io.quarkus.qe.test.failure.detector.cli;

import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.configuration.AppConfig;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.qe.test.failure.detector.output.Data;
import io.quarkus.qe.test.failure.detector.output.OutputChannel;
import io.quarkus.qe.test.failure.detector.project.ProjectSource;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilderProvider;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static io.quarkus.qe.test.failure.detector.cli.CommandUtils.parseDate;

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
            For the 'GITHUB_ACTION_ARTIFACTS' project source, this should be a link to the project workflow,
            for example "https://github.com/quarkus-qe/quarkus-test-suite/actions/workflows/daily.yaml"
            """, defaultValue = ".")
    String projectSourceArgument;

    @CommandLine.Option(order = 8, names = { "--debug" }, description = "Log debug messages", defaultValue = "false")
    boolean debug = false;

    @CommandLine.Option(order = 9, names = { "--lookback-days" }, description = """
            Number of days to look back for upstream changes in Quarkus repository.
            This determines how far back to clone commits when analyzing test failures.
            Default: 7 days
            """, defaultValue = "7")
    int lookbackDays = 7;

    @CommandLine.Option(order = 10, names = { "--from" }, description = """
            Reference date/time to look back from (default: now).
            Accepts formats:
            - dd.MM.yyyy (e.g., 10.1.2026 for January 10th, 2026)
            - yyyy-MM-dd (e.g., 2026-01-10)
            - ISO-8601 instant (e.g., 2026-01-10T00:00:00Z)
            When combined with --lookback-days, commits will be fetched from this date backwards.
            """)
    String from;

    @CommandLine.Option(order = 11, names = { "--history-file" }, description = """
            Where to find a file with history of previous executions of this tool.
            The history is created by this tool on every execution and stored in the current directory.
            It replaces the previous history file on this very path.
            """, defaultValue = "failure-history.json")
    String historyFilePath;

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

    @Inject
    Event<AppConfig> appConfigEvent;

    @Override
    public void run() {
        consoleLogger.setWriters(spec.commandLine().getOut(), spec.commandLine().getErr(), debug);

        appConfigEvent.fire(new AppConfig(lookbackDays, parseDate(from), historyFilePath));

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
