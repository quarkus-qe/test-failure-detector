package io.quarkus.qe.test.failure.detector.analyze.impl;

import io.quarkus.qe.test.failure.detector.TestLoggerProfile;
import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(TestLoggerProfile.class)
class FailuresAnalyzerImplTest {

    @Inject
    FailuresFinder failuresFinder;

    @Inject
    FailuresAnalyzer failuresAnalyzer;

    @Test
    void testSameClassDifferentMethods() {
        // This test verifies that multiple failures from the same test class
        // are grouped into a single root cause with HIGH confidence

        // Use test resources with failures in the same class
        Path testResourcePath = Paths.get("src/test/resources/failsafe-reports/single-module").toAbsolutePath();
        Collection<Failure> failures = failuresFinder.find(testResourcePath);

        // Analyze all failures
        List<RootCause> rootCauses = failures.stream()
                .map(failuresAnalyzer::analyze)
                .distinct()
                .toList();

        // Should have exactly 1 root cause (all failures from same class)
        assertEquals(1, rootCauses.size(), "Expected single root cause for failures in same class");

        RootCause rootCause = rootCauses.get(0);
        assertEquals(AnalyzedRootCause.ConfidenceLevel.HIGH, rootCause.confidence(),
                "Should have HIGH confidence for same class");
        assertTrue(rootCause.identifier().startsWith("CLASS:"),
                "Identifier should be class-based");
    }

    @Test
    void testMultiModule() {
        // This test verifies that failures from different modules
        // are grouped into separate root causes

        Path testResourcePath = Paths.get("src/test/resources/failsafe-reports/multi-module").toAbsolutePath();
        Collection<Failure> failures = failuresFinder.find(testResourcePath);

        assertTrue(failures.size() >= 2, "Should find failures from multiple modules");

        // Analyze all failures
        List<RootCause> rootCauses = failures.stream()
                .map(failuresAnalyzer::analyze)
                .distinct()
                .toList();

        // Should have at least 2 root causes (one per module: moduleA and nested/moduleB)
        assertTrue(rootCauses.size() >= 2,
                "Expected multiple root causes for failures in different modules");

        for (RootCause rc : rootCauses) {
            AnalyzedRootCause analyzedRc = (AnalyzedRootCause) rc;
            assertEquals(AnalyzedRootCause.ConfidenceLevel.HIGH, analyzedRc.confidence(),
                    "Should have HIGH confidence (first failure in each module)");
            assertNotNull(analyzedRc.modulePath(), "Module path should not be null");
        }
    }

    @Test
    void testErrorAndFailureTypes() {
        // This test verifies that both ERROR and FAILURE types are handled correctly

        Path testResourcePath = Paths.get("src/test/resources/failsafe-reports/error-and-failure").toAbsolutePath();
        Collection<Failure> failures = failuresFinder.find(testResourcePath);

        assertTrue(failures.size() >= 2, "Should find both ERROR and FAILURE types");

        // Verify we have both types
        boolean hasError = failures.stream()
                .anyMatch(f -> f.failureType() == Failure.FailureType.ERROR);
        boolean hasFailure = failures.stream()
                .anyMatch(f -> f.failureType() == Failure.FailureType.FAILURE);

        assertTrue(hasError, "Should have at least one ERROR");
        assertTrue(hasFailure, "Should have at least one FAILURE");

        // Analyze all failures
        List<RootCause> rootCauses = failures.stream()
                .map(failuresAnalyzer::analyze)
                .distinct()
                .toList();

        // Verify all root causes are AnalyzedRootCause instances
        for (RootCause rc : rootCauses) {
            assertInstanceOf(AnalyzedRootCause.class, rc, "All root causes should be AnalyzedRootCause instances");
            assertNotNull(rc.summary(), "Summary should not be null");
            assertNotNull(rc.metadata(), "Metadata should not be null");
        }
    }

    @Test
    void testNoFailures() {
        // This test verifies handling of projects with no failures

        Path testResourcePath = Paths.get("src/test/resources/failsafe-reports/no-failures").toAbsolutePath();
        Collection<Failure> failures = failuresFinder.find(testResourcePath);

        assertEquals(0, failures.size(), "Should find no failures");

        // Analyze empty collection (should not throw exception)
        List<RootCause> rootCauses = failures.stream()
                .map(failuresAnalyzer::analyze)
                .toList();

        assertEquals(0, rootCauses.size(), "Should produce no root causes");
    }

    @Test
    void testDeduplicationMetadata() {
        // This test verifies that deduplication metadata is tracked correctly

        Path testResourcePath = Paths.get("src/test/resources/failsafe-reports/single-module").toAbsolutePath();
        Collection<Failure> failures = failuresFinder.find(testResourcePath);

        if (failures.isEmpty()) {
            fail("Test requires at least one failure in single-module test resources");
        }

        // Analyze all failures sequentially
        RootCause firstRootCause = null;
        RootCause lastRootCause = null;

        for (Failure failure : failures) {
            RootCause rc = failuresAnalyzer.analyze(failure);
            if (firstRootCause == null) {
                firstRootCause = rc;
            }
            lastRootCause = rc;
        }

        assertNotNull(firstRootCause, "Should have at least one root cause");

        // First root cause should have 1 failure, 0 deduped
        assertEquals(1, firstRootCause.failures().size(),
                "First analysis should have 1 failure");
        assertEquals(0, firstRootCause.metadata().dedupedFailures(),
                "First analysis should have 0 deduplicated failures");

        // If we have multiple failures, verify deduplication tracking
        if (failures.size() > 1) {
            assertTrue(lastRootCause.failures().size() > 1,
                    "Last analysis should have accumulated failures");
            assertEquals(failures.size() - 1, lastRootCause.metadata().dedupedFailures(),
                    "Should track deduplicated count correctly");
        }
    }

    @Test
    void testPrimaryFailureMarking() {
        // This test verifies that the first failure is marked as primary

        Path testResourcePath = Paths.get("src/test/resources/failsafe-reports/single-module").toAbsolutePath();
        Collection<Failure> failures = failuresFinder.find(testResourcePath);

        if (failures.size() < 2) {
            // Skip test if we don't have enough failures
            return;
        }

        // Analyze all failures
        RootCause rootCause = null;
        for (Failure failure : failures) {
            rootCause = failuresAnalyzer.analyze(failure);
        }

        assertNotNull(rootCause);

        // First failure should be marked as primary
        assertTrue(rootCause.failures().get(0).isPrimaryFailure(),
                "First failure should be marked as primary");

        // Subsequent failures should not be marked as primary
        for (int i = 1; i < rootCause.failures().size(); i++) {
            assertFalse(rootCause.failures().get(i).isPrimaryFailure(),
                    "Subsequent failures should not be marked as primary");
        }
    }
}
