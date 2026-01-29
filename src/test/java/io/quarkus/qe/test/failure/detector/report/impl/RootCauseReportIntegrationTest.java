package io.quarkus.qe.test.failure.detector.report.impl;

import io.quarkus.qe.test.failure.detector.TestBeanProfile;
import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.qe.test.failure.detector.project.ProjectSource;
import io.quarkus.qe.test.failure.detector.report.RootCauseReportBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(TestBeanProfile.class)
class RootCauseReportIntegrationTest {

    @Inject
    FailuresFinder failuresFinder;

    @Inject
    FailuresAnalyzer failuresAnalyzer;

    @Test
    void testFullReportOutput() throws IOException {
        // Use multi-module test resources to get a comprehensive report
        Path testResourcePath = Paths.get("src/test/resources/failsafe-reports/multi-module").toAbsolutePath();
        Collection<Failure> failures = failuresFinder.find(testResourcePath);

        assertTrue(failures.size() >= 2, "Should find failures from multiple modules");

        // Create report builder
        RootCauseReportBuilder builder = new RootCauseReportBuilderImpl(
                ProjectSource.LOCAL_DIRECTORY,
                testResourcePath.toString()
        );

        // Analyze and add all failures
        for (Failure failure : failures) {
            RootCause rootCause = failuresAnalyzer.analyze(failure);
            builder.addRootCause(rootCause);
        }

        // Build and print report
        var report = builder.build();
        try (var reader = report.reader()) {
            String output = reader.readAllAsString();
            System.out.println("=== Report Output ===");
            System.out.println(output);
            System.out.println("=== End Report Output ===");

            // Verify report contains expected elements
            assertTrue(output.contains("Test Failure Analysis Report"), "Report should have title");
            assertTrue(output.contains("Root Cause #"), "Report should have root causes");
            assertTrue(output.contains("Identifier:"), "Report should have identifiers");
            assertTrue(output.contains("Module:"), "Report should have module paths");
            assertTrue(output.contains("Summary:"), "Report should have summaries");
            assertTrue(output.contains("Failures:"), "Report should have failure counts");
            assertTrue(output.contains("Affected Tests:"), "Report should list affected tests");
        }
    }

    @Test
    void testNoFailuresReport() throws IOException {
        // Test report when no failures are found
        Path testResourcePath = Paths.get("src/test/resources/failsafe-reports/no-failures").toAbsolutePath();
        Collection<Failure> failures = failuresFinder.find(testResourcePath);

        assertTrue(failures.isEmpty(), "Should find no failures");

        // Create report builder
        RootCauseReportBuilder builder = new RootCauseReportBuilderImpl(
                ProjectSource.LOCAL_DIRECTORY,
                testResourcePath.toString()
        );

        // Build report with no failures
        var report = builder.build();
        try (var reader = report.reader()) {
            String output = reader.readAllAsString();
            System.out.println("=== No Failures Report ===");
            System.out.println(output);
            System.out.println("=== End No Failures Report ===");

            // Verify report shows no failures
            assertTrue(output.contains("No test failures detected"), "Report should indicate no failures");
            assertTrue(output.contains(testResourcePath.toString()), "Report should show analyzed path");
        }
    }
}
