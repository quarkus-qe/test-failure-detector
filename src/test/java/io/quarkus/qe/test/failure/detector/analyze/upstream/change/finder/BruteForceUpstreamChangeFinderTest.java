package io.quarkus.qe.test.failure.detector.analyze.upstream.change.finder;

import io.quarkus.qe.test.failure.detector.TestBeanProfile;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory.HistoryData;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory.TrackedFailure;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory.TrackedFailure.FailureStatus;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Vetoed;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test BruteForceUpstreamChangeFinder using a fake git repository.
 * Uses the git-bisect-test repository which has a known history where:
 * - Older commits print "A"
 * - Middle commits print "B"
 * - Recent commits print "C" or "D"
 */
@QuarkusTest
@TestProfile(TestBeanProfile.class)
class BruteForceUpstreamChangeFinderTest {

    @Inject
    Logger logger;

    /**
     * Test that bisect can find which commit introduced a failure.
     * Uses a fake git repo with known history.
     */
    @Test
    void testBisectFindsFailingCommit(@TempDir Path tempDir) throws Exception {
        // Copy the test git repository
        Path testRepo = copyTestRepo(tempDir);

        // Create a mock failure history (empty - first run)
        HistoryData emptyHistory = HistoryData.empty();
        MockFailureHistory mockHistory = new MockFailureHistory(emptyHistory);

        // Create a mock failure for a test that we know fails
        MockFailure mockFailure = new MockFailure(
                "io.quarkus.test.HttpTest",
                "testHttpEndpoint",
                tempDir.resolve("module").toString()
        );

        // Create the finder with mock dependencies
        MockBruteForceUpstreamChangeFinder finder = new MockBruteForceUpstreamChangeFinder(
                logger,
                mockHistory,
                testRepo
        );

        // Run bisect
        RootCause.UpstreamChange change = finder.findUpstreamChange(mockFailure);

        // Verify we found a commit
        assertNotNull(change, "Should find upstream change");
        assertNotNull(change.gitCommitSHA(), "Should have commit SHA");
        logger.info("Found failing commit: " + change.gitCommitSHA());

        // Finalize and save
        finder.finalizeAndSaveHistory();

        // Verify history was saved
        HistoryData savedHistory = mockHistory.load();
        assertNotNull(savedHistory.quarkusCommit(), "Should have saved Quarkus commit");
        assertTrue(savedHistory.failures().size() > 0, "Should have tracked failures");
    }

    /**
     * Test that existing failures are not re-bisected.
     */
    @Test
    void testExistingFailureUsesCache(@TempDir Path tempDir) throws Exception {
        Path testRepo = copyTestRepo(tempDir);

        // Create history with an existing failure
        String knownCommit = getFirstCommit(testRepo);
        TrackedFailure existingFailure = new TrackedFailure(
                "io.quarkus.test.HttpTest",
                "testHttpEndpoint",
                tempDir.resolve("module").toString(),
                Instant.now().minusSeconds(86400),
                Instant.now().minusSeconds(3600),
                FailureStatus.EXISTING,
                knownCommit,
                "12345"
        );

        HistoryData historyWithFailure = new HistoryData(
                Instant.now().minusSeconds(3600),
                knownCommit,
                List.of(existingFailure),
                List.of(knownCommit)
        );

        MockFailureHistory mockHistory = new MockFailureHistory(historyWithFailure);

        MockFailure mockFailure = new MockFailure(
                "io.quarkus.test.HttpTest",
                "testHttpEndpoint",
                tempDir.resolve("module").toString()
        );

        MockBruteForceUpstreamChangeFinder finder = new MockBruteForceUpstreamChangeFinder(
                logger,
                mockHistory,
                testRepo
        );

        // This should use cached result, not run bisect
        RootCause.UpstreamChange change = finder.findUpstreamChange(mockFailure);

        assertNotNull(change, "Should return cached upstream change");
        assertEquals(knownCommit, change.gitCommitSHA(), "Should use cached commit");
        assertEquals("12345", change.prNumber(), "Should use cached PR");

        finder.finalizeAndSaveHistory();

        // Verify failure was marked as EXISTING
        HistoryData saved = mockHistory.load();
        assertTrue(saved.failures().stream()
                .anyMatch(f -> f.status() == FailureStatus.EXISTING));
    }

    /**
     * Test that resolved failures are properly tracked.
     */
    @Test
    void testResolvedFailuresAreMarked(@TempDir Path tempDir) throws Exception {
        Path testRepo = copyTestRepo(tempDir);

        // Create history with a failure that is now resolved (not in current run)
        String knownCommit = getFirstCommit(testRepo);
        TrackedFailure previousFailure = new TrackedFailure(
                "io.quarkus.test.OldTest",
                "testOldEndpoint",
                tempDir.resolve("old-module").toString(),
                Instant.now().minusSeconds(86400),
                Instant.now().minusSeconds(3600),
                FailureStatus.EXISTING,
                knownCommit,
                "99999"
        );

        HistoryData historyWithOldFailure = new HistoryData(
                Instant.now().minusSeconds(3600),
                knownCommit,
                List.of(previousFailure),
                List.of(knownCommit)
        );

        MockFailureHistory mockHistory = new MockFailureHistory(historyWithOldFailure);

        // Create a DIFFERENT failure (the old one is resolved)
        MockFailure newFailure = new MockFailure(
                "io.quarkus.test.NewTest",
                "testNewEndpoint",
                tempDir.resolve("new-module").toString()
        );

        MockBruteForceUpstreamChangeFinder finder = new MockBruteForceUpstreamChangeFinder(
                logger,
                mockHistory,
                testRepo
        );

        finder.findUpstreamChange(newFailure);
        finder.finalizeAndSaveHistory();

        // Verify old failure was marked as RESOLVED
        HistoryData saved = mockHistory.load();
        assertTrue(saved.failures().stream()
                        .anyMatch(f -> f.testClassName().equals("io.quarkus.test.OldTest") &&
                                f.status() == FailureStatus.RESOLVED),
                "Old failure should be marked as RESOLVED");
    }

    private Path copyTestRepo(Path tempDir) throws Exception {
        Path testRepo = tempDir.resolve("test-repo");
        Path sourceRepo = Paths.get("src/test/resources/git-bisect-test").toAbsolutePath();

        // Copy the entire git repository
        copyDirectory(sourceRepo, testRepo);

        return testRepo;
    }

    private void copyDirectory(Path source, Path target) throws Exception {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String getFirstCommit(Path repoPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "rev-list", "--first-parent", "-n", "1", "HEAD");
        pb.directory(repoPath.toFile());
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor();
        return output;
    }

    /**
     * Mock implementation of BruteForceUpstreamChangeFinder for testing.
     * Overrides repository setup to use the fake test repository.
     */
    @Vetoed
    private static class MockBruteForceUpstreamChangeFinder extends BruteForceUpstreamChangeFinder {

        MockBruteForceUpstreamChangeFinder(Logger logger, FailureHistory failureHistory, Path mockRepo) {
            super(logger, failureHistory);
            // Set configuration manually for tests
            this.lookbackDays = 7;
            this.from = Instant.now();
            // Set the fake repository path
            this.quarkusRepoPath = mockRepo;
            this.testSuiteRepoPath = mockRepo; // Use same for simplicity
        }

        @Override
        protected boolean buildQuarkus() {
            // Skip actual Maven build in tests
            logger.info("Mock: Skipping Quarkus build");
            return true;
        }

        @Override
        protected boolean runTest(Failure failure) {
            // Simulate test execution by checking git repo output
            // In the test repo, we can check what the Main.java prints
            try {
                // Read Main.java to see what it prints
                Path mainJava = quarkusRepoPath.resolve("src/main/java/io/quarkus/test/Main.java");
                if (Files.exists(mainJava)) {
                    String content = Files.readString(mainJava);
                    // If it prints "D", test fails; otherwise passes
                    boolean testFails = content.contains("System.out.println(\"D\")");
                    logger.info("Mock test " + (testFails ? "FAILED" : "PASSED") + " for commit");
                    return !testFails;
                }
            } catch (Exception e) {
                logger.error("Error in mock test: " + e.getMessage());
            }
            // Default: test passes
            return true;
        }
    }

    /**
     * Mock FailureHistory for testing.
     */
    private static class MockFailureHistory implements FailureHistory {
        private HistoryData data;

        MockFailureHistory(HistoryData initialData) {
            this.data = initialData;
        }

        @Override
        public HistoryData load() {
            return data;
        }

        @Override
        public void save(HistoryData history) {
            this.data = history;
        }
    }

    /**
     * Mock Failure for testing.
     */
    private record MockFailure(String testClassName, String testMethodName, String modulePath) implements Failure {
        @Override
        public String failureMessage() {
            return "Mock failure message";
        }

        @Override
        public FailureType failureType() {
            return FailureType.FAILURE;
        }

        @Override
        public String throwableClass() {
            return "java.lang.AssertionError";
        }

        @Override
        public String testRunLog() {
            return "Mock test run log";
        }
    }
}
