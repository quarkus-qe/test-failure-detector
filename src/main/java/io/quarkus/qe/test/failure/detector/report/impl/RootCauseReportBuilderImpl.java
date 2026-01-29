package io.quarkus.qe.test.failure.detector.report.impl;

import io.quarkus.qe.test.failure.detector.analyze.FailureDetails;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.project.ProjectSource;
import io.quarkus.qe.test.failure.detector.report.RootCauseReport;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

final class RootCauseReportBuilderImpl implements RootCauseReportBuilder {

    private final List<RootCause> rootCauses;
    private final ProjectSource projectSource;
    private final String projectSourceArgument;

    RootCauseReportBuilderImpl(ProjectSource projectSource, String projectSourceArgument) {
        this.rootCauses = new ArrayList<>();
        this.projectSource = projectSource;
        this.projectSourceArgument = projectSourceArgument;
    }

    @Override
    public RootCauseReportBuilder addRootCause(RootCause rootCause) {
        boolean alreadyExists = rootCauses.stream()
                .anyMatch(existing -> existing.identifier().equals(rootCause.identifier()));

        if (!alreadyExists) {
            rootCauses.add(rootCause);
        }
        return this;
    }

    @Override
    public RootCauseReport build() {
        return new RootCauseReportImpl(rootCauses, projectSource, projectSourceArgument);
    }

    /**
     * Concrete implementation of RootCauseReport.
     */
    private record RootCauseReportImpl(Reader reader) implements RootCauseReport {

        private RootCauseReportImpl(List<RootCause> rootCauses, ProjectSource projectSource, String projectSourceArgument) {
            this(createReader(List.copyOf(rootCauses), projectSource, projectSourceArgument));
        }

        private static Reader createReader(List<RootCause> rootCauses, ProjectSource projectSource, String projectSourceArgument) {
            var resultBuilder = new StringBuilder();
            createData(resultBuilder, rootCauses, projectSource, projectSourceArgument);
            return Reader.of(resultBuilder.toString());
        }

        private static void createData(StringBuilder resultBuilder, List<RootCause> rootCauses, ProjectSource projectSource, String projectSourceArgument) {
            if (rootCauses.isEmpty()) {
                resultBuilder.append(System.lineSeparator())
                        .append("✓ No test failures detected.");

                // Add context about what was analyzed
                if (projectSource == ProjectSource.GITHUB_ACTION_ARTIFACTS && projectSourceArgument != null) {
                    resultBuilder.append(System.lineSeparator())
                            .append(System.lineSeparator())
                            .append("Analyzed: ").append(projectSourceArgument);
                } else if (projectSourceArgument != null) {
                    resultBuilder.append(System.lineSeparator())
                            .append(System.lineSeparator())
                            .append("Analyzed: ").append(projectSourceArgument);
                }
                return;
            }

            resultBuilder.append(System.lineSeparator())
                    .append("=== Test Failure Analysis Report ===")
                    .append(System.lineSeparator());
            resultBuilder.append("Found ").append(rootCauses.size()).append(" distinct root cause(s):\n");

            for (int i = 0; i < rootCauses.size(); i++) {
                printRootCause(i + 1, rootCauses.get(i), resultBuilder);
            }
            
            resultBuilder.append(System.lineSeparator()).append("=== End of Report ===").append(System.lineSeparator());
        }

        private static void printRootCause(int index, RootCause rootCause, StringBuilder resultBuilder) {
            resultBuilder.append("Root Cause #").append(index).append(" [").append(rootCause.confidence()).append(" Confidence]");
            resultBuilder.append("  Identifier: ").append(rootCause.identifier());
            resultBuilder.append("  Module: ").append(rootCause.modulePath());
            resultBuilder.append("  Summary: ").append(rootCause.summary());
            resultBuilder.append("  Failures: ").append(rootCause.failures().size()).append(" (")
                    .append(rootCause.failures().size() - rootCause.metadata().dedupedFailures())
                    .append(" primary + ").append(rootCause.metadata().dedupedFailures()).append(" deduplicated)");

            resultBuilder.append("  Affected Tests:");
            for (FailureDetails failure : rootCause.failures()) {
                String marker = failure.isPrimaryFailure() ? "[PRIMARY]" : "[DEDUPED]";
                resultBuilder.append("    ").append(marker).append(" ")
                        .append(getSimpleClassName(failure.testClassName()))
                        .append(".").append(failure.testMethodName());
                if (failure.failureMessage() != null && !failure.failureMessage().isBlank()) {
                    String truncatedMessage = truncateMessage(failure.failureMessage());
                    resultBuilder.append("      └─ ").append(truncatedMessage);
                }
            }
            resultBuilder.append(System.lineSeparator());
        }

        private static String getSimpleClassName(String fullyQualifiedName) {
            if (fullyQualifiedName == null) {
                return "Unknown";
            }
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
        }

        private static String truncateMessage(String message) {
            if (message == null) {
                return "";
            }
            // Replace newlines with spaces for compact display
            String singleLine = message.replaceAll("\\s+", " ").trim();
            if (singleLine.length() <= 100) {
                return singleLine;
            }
            return singleLine.substring(0, 100 - 3) + "...";
        }
    }
}
