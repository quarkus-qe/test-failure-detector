package io.quarkus.qe.test.failure.detector.analyze.upstream.change.finder;

import io.quarkus.qe.test.failure.detector.TestBeanProfile;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory.HistoryData;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory.TrackedFailure;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory.TrackedFailure.FailureStatus;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.configuration.AppConfig;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.lifecycle.OnCommandExit;
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
 * Test NaiveUpstreamChangeFinder using a fake git repository.
 * Uses the git-bisect-test repository which has a known history where:
 * - Older commits print "A"
 * - Middle commits print "B"
 * - Recent commits print "C" or "D"
 */
@QuarkusTest
@TestProfile(TestBeanProfile.class)
class NaiveUpstreamChangeFinderTest {

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
        MockNaiveUpstreamChangeFinder finder = new MockNaiveUpstreamChangeFinder(
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
        finder.finalizeAndSaveHistory(new OnCommandExit());

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

        MockNaiveUpstreamChangeFinder finder = new MockNaiveUpstreamChangeFinder(
                logger,
                mockHistory,
                testRepo
        );

        // This should use cached result, not run bisect
        RootCause.UpstreamChange change = finder.findUpstreamChange(mockFailure);

        assertNotNull(change, "Should return cached upstream change");
        assertEquals(knownCommit, change.gitCommitSHA(), "Should use cached commit");
        assertEquals("12345", change.prNumber(), "Should use cached PR");

        finder.finalizeAndSaveHistory(new OnCommandExit());

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

        MockNaiveUpstreamChangeFinder finder = new MockNaiveUpstreamChangeFinder(
                logger,
                mockHistory,
                testRepo
        );

        finder.findUpstreamChange(newFailure);
        finder.finalizeAndSaveHistory(new OnCommandExit());

        // Verify old failure was marked as RESOLVED
        HistoryData saved = mockHistory.load();
        assertTrue(saved.failures().stream()
                        .anyMatch(f -> f.testClassName().equals("io.quarkus.test.OldTest") &&
                                f.status() == FailureStatus.RESOLVED),
                "Old failure should be marked as RESOLVED");
    }

    /**
     * Test that binary search finds the failing commit.
     */
    @Test
    void testBinarySearchFindsFailingCommit(@TempDir Path tempDir) throws Exception {
        Path testRepo = copyTestRepo(tempDir);

        HistoryData emptyHistory = HistoryData.empty();
        MockFailureHistory mockHistory = new MockFailureHistory(emptyHistory);

        MockFailure mockFailure = new MockFailure(
                "io.quarkus.test.BinaryTest",
                "testBinary",
                tempDir.resolve("module").toString()
        );

        MockNaiveUpstreamChangeFinder finder = new MockNaiveUpstreamChangeFinder(
                logger,
                mockHistory,
                testRepo
        );
        finder.setBisectStrategy(AppConfig.BisectStrategy.BINARY);

        RootCause.UpstreamChange change = finder.findUpstreamChange(mockFailure);

        assertNotNull(change, "Binary search should find upstream change");
        assertNotNull(change.gitCommitSHA(), "Should have commit SHA");
        logger.info("Binary search found failing commit: " + change.gitCommitSHA());

        finder.finalizeAndSaveHistory(new OnCommandExit());
    }

    /**
     * Test that linear search finds the failing commit.
     */
    @Test
    void testLinearSearchFindsFailingCommit(@TempDir Path tempDir) throws Exception {
        Path testRepo = copyTestRepo(tempDir);

        HistoryData emptyHistory = HistoryData.empty();
        MockFailureHistory mockHistory = new MockFailureHistory(emptyHistory);

        MockFailure mockFailure = new MockFailure(
                "io.quarkus.test.LinearTest",
                "testLinear",
                tempDir.resolve("module").toString()
        );

        MockNaiveUpstreamChangeFinder finder = new MockNaiveUpstreamChangeFinder(
                logger,
                mockHistory,
                testRepo
        );
        finder.setBisectStrategy(AppConfig.BisectStrategy.LINEAR);

        RootCause.UpstreamChange change = finder.findUpstreamChange(mockFailure);

        assertNotNull(change, "Linear search should find upstream change");
        assertNotNull(change.gitCommitSHA(), "Should have commit SHA");
        logger.info("Linear search found failing commit: " + change.gitCommitSHA());

        finder.finalizeAndSaveHistory(new OnCommandExit());
    }

    /**
     * Test that both strategies find the same commit.
     */
    @Test
    void testBinaryAndLinearFindSameCommit(@TempDir Path tempDir) throws Exception {
        Path testRepo1 = copyTestRepo(tempDir.resolve("binary"));
        Path testRepo2 = copyTestRepo(tempDir.resolve("linear"));

        // Test with binary search
        HistoryData emptyHistory1 = HistoryData.empty();
        MockFailureHistory mockHistory1 = new MockFailureHistory(emptyHistory1);

        MockFailure mockFailure1 = new MockFailure(
                "io.quarkus.test.CompareTest",
                "testCompare",
                tempDir.resolve("module1").toString()
        );

        MockNaiveUpstreamChangeFinder binaryFinder = new MockNaiveUpstreamChangeFinder(
                logger,
                mockHistory1,
                testRepo1
        );
        binaryFinder.setBisectStrategy(AppConfig.BisectStrategy.BINARY);

        RootCause.UpstreamChange binaryChange = binaryFinder.findUpstreamChange(mockFailure1);
        binaryFinder.finalizeAndSaveHistory(new OnCommandExit());

        // Test with linear search
        HistoryData emptyHistory2 = HistoryData.empty();
        MockFailureHistory mockHistory2 = new MockFailureHistory(emptyHistory2);

        MockFailure mockFailure2 = new MockFailure(
                "io.quarkus.test.CompareTest",
                "testCompare",
                tempDir.resolve("module2").toString()
        );

        MockNaiveUpstreamChangeFinder linearFinder = new MockNaiveUpstreamChangeFinder(
                logger,
                mockHistory2,
                testRepo2
        );
        linearFinder.setBisectStrategy(AppConfig.BisectStrategy.LINEAR);

        RootCause.UpstreamChange linearChange = linearFinder.findUpstreamChange(mockFailure2);
        linearFinder.finalizeAndSaveHistory(new OnCommandExit());

        // Both should find the same commit
        assertNotNull(binaryChange, "Binary search should find commit");
        assertNotNull(linearChange, "Linear search should find commit");
        assertEquals(binaryChange.gitCommitSHA(), linearChange.gitCommitSHA(),
                "Binary and linear search should find the same commit");

        logger.info("Both strategies found commit: " + binaryChange.gitCommitSHA());
    }

    /**
     * Test that binary search tests fewer commits than linear.
     */
    @Test
    void testBinarySearchIsMoreEfficient(@TempDir Path tempDir) throws Exception {
        Path testRepo1 = copyTestRepo(tempDir.resolve("binary-eff"));
        Path testRepo2 = copyTestRepo(tempDir.resolve("linear-eff"));

        // Test with binary search
        HistoryData emptyHistory1 = HistoryData.empty();
        MockFailureHistory mockHistory1 = new MockFailureHistory(emptyHistory1);

        MockFailure mockFailure1 = new MockFailure(
                "io.quarkus.test.EfficiencyTest",
                "testEfficiency",
                tempDir.resolve("module1").toString()
        );

        MockNaiveUpstreamChangeFinder binaryFinder = new MockNaiveUpstreamChangeFinder(
                logger,
                mockHistory1,
                testRepo1
        );
        binaryFinder.setBisectStrategy(AppConfig.BisectStrategy.BINARY);

        binaryFinder.findUpstreamChange(mockFailure1);
        binaryFinder.finalizeAndSaveHistory(new OnCommandExit());

        int binaryTestedCount = mockHistory1.load().testedCommits().size();

        // Test with linear search
        HistoryData emptyHistory2 = HistoryData.empty();
        MockFailureHistory mockHistory2 = new MockFailureHistory(emptyHistory2);

        MockFailure mockFailure2 = new MockFailure(
                "io.quarkus.test.EfficiencyTest",
                "testEfficiency",
                tempDir.resolve("module2").toString()
        );

        MockNaiveUpstreamChangeFinder linearFinder = new MockNaiveUpstreamChangeFinder(
                logger,
                mockHistory2,
                testRepo2
        );
        linearFinder.setBisectStrategy(AppConfig.BisectStrategy.LINEAR);

        linearFinder.findUpstreamChange(mockFailure2);
        linearFinder.finalizeAndSaveHistory(new OnCommandExit());

        int linearTestedCount = mockHistory2.load().testedCommits().size();

        logger.info("Binary search tested " + binaryTestedCount + " commits");
        logger.info("Linear search tested " + linearTestedCount + " commits");

        // Binary should test significantly fewer commits
        assertTrue(binaryTestedCount < linearTestedCount,
                "Binary search should test fewer commits than linear (binary: " + binaryTestedCount +
                        ", linear: " + linearTestedCount + ")");
    }

    private Path copyTestRepo(Path tempDir) throws Exception {
        // Create the temp directory if it doesn't exist
        Files.createDirectories(tempDir);

        // Extract the test repository from tarball
        Path tarball = Paths.get("src/test/resources/git-bisect-test.tar.gz").toAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tarball.toString(), "-C", tempDir.toString());
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            String error = new String(process.getErrorStream().readAllBytes());
            throw new RuntimeException("Failed to extract test repository: " + error);
        }

        return tempDir.resolve("git-bisect-test");
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
     * Mock implementation of NaiveUpstreamChangeFinder for testing.
     * Overrides repository setup to use the fake test repository.
     */
    @Vetoed
    private static class MockNaiveUpstreamChangeFinder extends NaiveUpstreamChangeFinder {

        MockNaiveUpstreamChangeFinder(Logger logger, FailureHistory failureHistory, Path mockRepo) {
            super(logger, failureHistory);
            // Set configuration manually for tests
            this.lookbackDays = 7;
            this.from = Instant.now();
            this.bisectStrategy = AppConfig.BisectStrategy.BINARY; // Default to binary
            // Set the fake repository path
            this.quarkusRepoPath = mockRepo;
            this.testSuiteRepoPath = mockRepo; // Use same for simplicity
        }

        // Allow overriding the strategy for testing
        void setBisectStrategy(AppConfig.BisectStrategy strategy) {
            this.bisectStrategy = strategy;
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
