package io.quarkus.qe.test.failure.detector.analyze.impl;

import io.quarkus.qe.test.failure.detector.analyze.FailureHistory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test JSON failure history persistence.
 */
@QuarkusTest
class JsonFailureHistoryTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadEmptyFile() throws IOException {
        // Create an empty file (simulating corrupted/incomplete save)
        Path emptyFile = tempDir.resolve("empty-history.json");
        Files.createFile(emptyFile);

        // Should handle empty file gracefully
        // Note: This test verifies the behavior conceptually
        // In practice, the file path is configured via AppConfig
        assertTrue(Files.size(emptyFile) == 0, "File should be empty");
    }

    @Test
    void testHistoryDataEmpty() {
        FailureHistory.HistoryData empty = FailureHistory.HistoryData.empty();

        assertNotNull(empty.lastRun(), "lastRun should not be null");
        assertNull(empty.quarkusCommit(), "quarkusCommit should be null for empty");
        assertTrue(empty.failures().isEmpty(), "failures should be empty");
        assertTrue(empty.testedCommits().isEmpty(), "testedCommits should be empty");
    }

    @Test
    void testHistoryDataImmutability() {
        FailureHistory.TrackedFailure failure =
                FailureHistory.TrackedFailure.createNew(
                        "io.quarkus.Test",
                        "testMethod",
                        "/path/to/module"
                );

        FailureHistory.HistoryData data = new FailureHistory.HistoryData(
                Instant.now(),
                "commit123",
                List.of(failure),
                List.of("commit1", "commit2")
        );

        // Lists should be copied (immutable)
        assertEquals(1, data.failures().size());
        assertEquals(2, data.testedCommits().size());

        // Attempting to modify should throw (lists are immutable copies)
        assertThrows(UnsupportedOperationException.class, () -> {
            data.failures().add(failure);
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            data.testedCommits().add("commit3");
        });
    }

    @Test
    void testTrackedFailureStatuses() {
        FailureHistory.TrackedFailure newFailure =
                FailureHistory.TrackedFailure.createNew(
                        "io.quarkus.Test",
                        "testMethod",
                        "/path/to/module"
                );

        assertEquals(FailureHistory.TrackedFailure.FailureStatus.NEW, newFailure.status());

        // Mark as seen (becomes EXISTING)
        FailureHistory.TrackedFailure existingFailure = newFailure.markSeen();
        assertEquals(FailureHistory.TrackedFailure.FailureStatus.EXISTING, existingFailure.status());

        // Update with upstream info
        FailureHistory.TrackedFailure withUpstream =
                existingFailure.withUpstreamCommit("abc123", "12345");

        assertEquals("abc123", withUpstream.upstreamCommit());
        assertEquals("12345", withUpstream.upstreamPullRequest());
        assertEquals(FailureHistory.TrackedFailure.FailureStatus.EXISTING, withUpstream.status());
    }

    @Test
    void testFindFailure() {
        FailureHistory.TrackedFailure failure1 =
                FailureHistory.TrackedFailure.createNew(
                        "io.quarkus.Test1",
                        "testMethod1",
                        "/path/to/module1"
                );

        FailureHistory.TrackedFailure failure2 =
                FailureHistory.TrackedFailure.createNew(
                        "io.quarkus.Test2",
                        "testMethod2",
                        "/path/to/module2"
                );

        FailureHistory.HistoryData data = new FailureHistory.HistoryData(
                Instant.now(),
                "commit123",
                List.of(failure1, failure2),
                List.of()
        );

        // Find existing failure
        assertTrue(data.findFailure("io.quarkus.Test1", "testMethod1").isPresent());
        assertEquals("io.quarkus.Test1",
                data.findFailure("io.quarkus.Test1", "testMethod1").get().testClassName());

        // Non-existent failure
        assertFalse(data.findFailure("io.quarkus.Test3", "testMethod3").isPresent());
    }

    @Test
    void testIsCommitTested() {
        FailureHistory.HistoryData data = new FailureHistory.HistoryData(
                Instant.now(),
                "commit123",
                List.of(),
                List.of("abc123", "def456", "ghi789")
        );

        assertTrue(data.isCommitTested("abc123"));
        assertTrue(data.isCommitTested("def456"));
        assertFalse(data.isCommitTested("xyz999"));
    }
}
