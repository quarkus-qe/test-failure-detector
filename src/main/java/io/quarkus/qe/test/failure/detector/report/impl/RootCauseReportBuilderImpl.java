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
            resultBuilder.append(System.lineSeparator());
            resultBuilder.append("Root Cause #").append(index).append(" [").append(rootCause.confidence()).append(" Confidence]").append(System.lineSeparator());
            resultBuilder.append("  Identifier: ").append(rootCause.identifier()).append(System.lineSeparator());
            resultBuilder.append("  Module: ").append(rootCause.modulePath()).append(System.lineSeparator());
            resultBuilder.append("  Summary: ").append(rootCause.summary()).append(System.lineSeparator());
            resultBuilder.append("  Failures: ").append(rootCause.failures().size()).append(" (")
                    .append(rootCause.failures().size() - rootCause.metadata().dedupedFailures())
                    .append(" primary + ").append(rootCause.metadata().dedupedFailures()).append(" deduplicated)").append(System.lineSeparator());

            // Print upstream change information if available
            if (rootCause.upstreamChange() != null && rootCause.upstreamChange().gitCommitSHA() != null) {
                RootCause.UpstreamChange change = rootCause.upstreamChange();
                resultBuilder.append("  Upstream Change:").append(System.lineSeparator());
                resultBuilder.append("    Commit: ").append(change.gitCommitSHA()).append(System.lineSeparator());
                resultBuilder.append("    GitHub: https://github.com/quarkusio/quarkus/commit/")
                        .append(change.gitCommitSHA()).append(System.lineSeparator());
                if (change.prNumber() != null && !change.prNumber().isBlank()) {
                    resultBuilder.append("    PR: https://github.com/quarkusio/quarkus/pull/")
                            .append(change.prNumber()).append(System.lineSeparator());
                }
                if (change.gitCommitMessage() != null && !change.gitCommitMessage().isBlank()) {
                    String truncatedMessage = truncateMessage(change.gitCommitMessage());
                    resultBuilder.append("    Message: ").append(truncatedMessage).append(System.lineSeparator());
                }
            } else if (rootCause.upstreamChange() != null) {
                // We have an UpstreamChange but no commit - check the failure reason
                RootCause.FailureReason reason = rootCause.upstreamChange().failureReason();
                switch (reason) {
                    case CANNOT_REPRODUCE -> {
                        resultBuilder.append("  Upstream Change: Cannot reproduce the failure").append(System.lineSeparator());
                        resultBuilder.append("    Test passed on all tested Quarkus commits (oldest to newest)").append(System.lineSeparator());
                        resultBuilder.append("    This indicates:").append(System.lineSeparator());
                        resultBuilder.append("      - Test is flaky (intermittent failure)").append(System.lineSeparator());
                        resultBuilder.append("      - Environmental differences between test suite run and bisect environment").append(System.lineSeparator());
                    }
                    case BUILD_FAILED -> {
                        resultBuilder.append("  Upstream Change: Unable to identify").append(System.lineSeparator());
                        resultBuilder.append("    Quarkus build failed during bisect").append(System.lineSeparator());
                        resultBuilder.append("    Check workflow logs for build errors").append(System.lineSeparator());
                    }
                    case OLDEST_COMMIT_FAILED -> {
                        resultBuilder.append("  Upstream Change: Unable to identify").append(System.lineSeparator());
                        resultBuilder.append("    Test failed on oldest commit in lookback window").append(System.lineSeparator());
                        resultBuilder.append("    Failure was likely introduced before the lookback range").append(System.lineSeparator());
                        resultBuilder.append("    Consider increasing the lookback period").append(System.lineSeparator());
                    }
                    default -> {
                        resultBuilder.append("  Upstream Change: Unable to identify").append(System.lineSeparator());
                        resultBuilder.append("    Check recent Quarkus commits: https://github.com/quarkusio/quarkus/commits/main").append(System.lineSeparator());
                    }
                }
            } else {
                // No UpstreamChange at all (shouldn't happen, but handle it)
                resultBuilder.append("  Upstream Change: Not analyzed").append(System.lineSeparator());
            }

            resultBuilder.append("  Affected Tests:").append(System.lineSeparator());
            for (FailureDetails failure : rootCause.failures()) {
                String marker = failure.isPrimaryFailure() ? "[PRIMARY]" : "[DEDUPED]";
                resultBuilder.append("    ").append(marker).append(" ")
                        .append(getSimpleClassName(failure.testClassName()))
                        .append(".").append(failure.testMethodName()).append(System.lineSeparator());
                if (failure.failureMessage() != null && !failure.failureMessage().isBlank()) {
                    String truncatedMessage = truncateMessage(failure.failureMessage());
                    resultBuilder.append("      └─ ").append(truncatedMessage).append(System.lineSeparator());
                }
            }
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
