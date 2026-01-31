package io.quarkus.qe.test.failure.detector.analyze.upstream.change.finder;

import io.quarkus.qe.test.failure.detector.analyze.FailureHistory;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory.HistoryData;
import io.quarkus.qe.test.failure.detector.analyze.FailureHistory.TrackedFailure;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.analyze.UpstreamChangeFinder;
import io.quarkus.qe.test.failure.detector.configuration.AppConfig;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.lifecycle.OnCommandExit;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import io.quarkus.runtime.Shutdown;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds upstream changes (in Quarkus repository) that caused test failures.
 * <p>
 * Uses brute-force git bisect approach:
 * 1. Loads previous failure history
 * 2. Identifies NEW failures (not seen in previous run)
 * 3. For each NEW failure, runs git bisect on Quarkus repo:
 *    - Builds each commit with `mvn -Dquickly`
 *    - Runs the failing test
 *    - Finds the first commit that introduced the failure
 * 4. Updates failure history with results
 * <p>
 * NOTE: This implementation has a design limitation - the UpstreamChangeFinder interface
 * doesn't pass the Failure being analyzed. We work around this by using ThreadLocal
 * to track the current failure context set by the caller.
 */
@ApplicationScoped
class NaiveUpstreamChangeFinder implements UpstreamChangeFinder {

    private static final String QUARKUS_REPO_URL = "https://github.com/quarkusio/quarkus.git";
    private static final String TEST_SUITE_REPO_URL = "https://github.com/quarkus-qe/quarkus-test-suite.git";

    protected final Logger logger;
    protected final FailureHistory failureHistory;

    protected int lookbackDays;
    protected Instant from;
    protected AppConfig.BisectStrategy bisectStrategy;

    // Stateful tracking for the current analysis session
    protected HistoryData previousHistory;
    protected List<String> untestedCommits;
    protected List<String> testedCommitsThisSession;
    protected List<TrackedFailure> currentSessionFailures;
    protected Path quarkusRepo;
    protected Path testSuiteRepo;
    protected boolean initialized = false;

    // For testing: allow overriding repository paths
    protected String quarkusRepoUrl = QUARKUS_REPO_URL;
    protected String testSuiteRepoUrl = TEST_SUITE_REPO_URL;
    protected Path quarkusRepoPath;
    protected Path testSuiteRepoPath;

    NaiveUpstreamChangeFinder(Logger logger,
                                   FailureHistory failureHistory) {
        this.logger = logger;
        this.failureHistory = failureHistory;
        this.lookbackDays = -1;
    }

    void updateConfiguration(@Observes AppConfig appConfig) {
        this.lookbackDays = appConfig.lookbackDays();
        this.from = appConfig.from();
        this.bisectStrategy = appConfig.bisectStrategy();
        this.testSuiteRepoUrl = appConfig.testSuiteRepoUrl();
    }

    @Override
    public RootCause.UpstreamChange findUpstreamChange(Failure failure) {
        Objects.requireNonNull(failure);

        // Initialize on first call
        if (!initialized) {
            initialize();
        }

        // Check if this failure is NEW or EXISTING
        Optional<TrackedFailure> existing = previousHistory.findFailure(
                failure.testClassName(), failure.testMethodName());

        TrackedFailure trackedFailure;
        RootCause.UpstreamChange upstreamChange = null;

        if (existing.isPresent()) {
            logger.info("EXISTING failure: " + failure.testClassName() + "#" + failure.testMethodName() +
                    " - using previous upstream commit info");
            TrackedFailure previous = existing.get();
            trackedFailure = previous.markSeen();

            if (previous.upstreamCommit() != null) {
                // not sure if this is ever non-null as we try to only check out the new commits (not saved in history)
                String commitMessage = getCommitMessage(quarkusRepo, previous.upstreamCommit());

                upstreamChange = new RootCause.UpstreamChange(
                        previous.upstreamCommit(),
                        previous.upstreamPullRequest(),
                        commitMessage,
                        RootCause.FailureReason.FOUND
                );
            }
        } else {
            logger.info("NEW failure detected: " + failure.testClassName() + "#" + failure.testMethodName() +
                    " - starting bisect");

            // Create new tracked failure
            trackedFailure = TrackedFailure.createNew(
                    failure.testClassName(),
                    failure.testMethodName(),
                    failure.modulePath()
            );

            // For NEW failures, test commits since last run
            // If this test was passing in the last run and failing now,
            // it must have broken in the new commits since then
            // All NEW failures share the same commit range (untestedCommits)
            BisectResult result = bisectFailure(failure, untestedCommits);

            if (result.foundCommit()) {
                logger.info("Found culprit commit: " + result.commit() + " (PR: " + result.pullRequest() + ")");
                trackedFailure = trackedFailure.withUpstreamCommit(result.commit(), result.pullRequest());
                upstreamChange = new RootCause.UpstreamChange(
                        result.commit(),
                        result.pullRequest(),
                        result.commitMessage(),
                        RootCause.FailureReason.FOUND
                );
                testedCommitsThisSession.addAll(result.testedCommits());
            } else {
                logger.info("Could not identify upstream commit for failure - reason: " + result.reason());
                upstreamChange = new RootCause.UpstreamChange(null, null, null, result.reason());
            }
        }

        // Track this failure for the current session
        currentSessionFailures.add(trackedFailure);

        return upstreamChange;
    }

    /**
     * Finalize the analysis session and save updated history.
     * This should be called after all failures have been analyzed.
     * It marks previous failures that are no longer failing as RESOLVED.
     */
    void finalizeAndSaveHistory(@Observes OnCommandExit ignored) {
        if (!initialized) {
            logger.info("Not initialized, skipping history save");
            return;
        }

        logger.info("Finalizing analysis session");

        // Mark previous failures that are not in current session as RESOLVED
        List<TrackedFailure> allFailures = new ArrayList<>(currentSessionFailures);

        for (TrackedFailure previousFailure : previousHistory.failures()) {
            // Check if this failure was seen in the current session
            boolean stillFailing = currentSessionFailures.stream()
                    .anyMatch(f -> f.testClassName().equals(previousFailure.testClassName()) &&
                            f.testMethodName().equals(previousFailure.testMethodName()));

            if (!stillFailing && previousFailure.status() != TrackedFailure.FailureStatus.RESOLVED) {
                // Mark as resolved
                TrackedFailure resolved = new TrackedFailure(
                        previousFailure.testClassName(),
                        previousFailure.testMethodName(),
                        previousFailure.modulePath(),
                        previousFailure.firstSeen(),
                        Instant.now(),
                        TrackedFailure.FailureStatus.RESOLVED,
                        previousFailure.upstreamCommit(),
                        previousFailure.upstreamPullRequest()
                );
                allFailures.add(resolved);
                logger.info("RESOLVED failure: " + previousFailure.testClassName() + "#" +
                        previousFailure.testMethodName());
            } else if (previousFailure.status() == TrackedFailure.FailureStatus.RESOLVED) {
                // Keep resolved failures for historical tracking
                allFailures.add(previousFailure);
            }
        }

        // Get current Quarkus commit
        String currentQuarkusCommit = getQuarkusHeadCommit(quarkusRepo);

        // Combine tested commits
        List<String> allTestedCommits = new ArrayList<>(previousHistory.testedCommits());
        allTestedCommits.addAll(testedCommitsThisSession);

        // Save updated history
        HistoryData updatedHistory = new HistoryData(
                Instant.now(),
                currentQuarkusCommit,
                allFailures,
                allTestedCommits
        );

        failureHistory.save(updatedHistory);
        logger.info("Saved updated history: " + allFailures.size() + " total failures tracked (" +
                currentSessionFailures.size() + " current, " +
                allFailures.stream().filter(f -> f.status() == TrackedFailure.FailureStatus.RESOLVED).count() +
                " resolved)");
    }

    /**
     * Get the current HEAD commit of the Quarkus repository.
     */
    private String getQuarkusHeadCommit(Path repoPath) {
        return runCommand(repoPath, "git", "rev-parse", "HEAD").trim();
    }

    /**
     * Initialize the finder - load history and set up repositories.
     */
    private void initialize() {
        initialized = true;

        logger.info("Initializing NaiveUpstreamChangeFinder");

        // Load previous history
        previousHistory = failureHistory.load();
        logger.info("Loaded previous history: " + previousHistory.failures().size() + " tracked failures, " +
                previousHistory.testedCommits().size() + " tested commits");

        // Initialize session tracking
        currentSessionFailures = new ArrayList<>();
        testedCommitsThisSession = new ArrayList<>();

        // Set up Quarkus repository
        quarkusRepo = setupQuarkusRepository();

        // Set up test suite repository
        testSuiteRepo = setupTestSuiteRepository();

        // Get commits to test (either last 50 if no history, or new commits since last run)
        untestedCommits = getUntestedCommits();
    }

    /**
     * Set up the Quarkus repository (clone or update).
     */
    protected Path setupQuarkusRepository() {
        if (quarkusRepoPath != null) {
            // Use provided path (for testing)
            return quarkusRepoPath;
        }
        return setupGitRepository(quarkusRepoUrl, createTemporaryDirectory("quarkus-clone"), "Quarkus");
    }

    /**
     * Set up the test suite repository (clone or update).
     */
    protected Path setupTestSuiteRepository() {
        if (testSuiteRepoPath != null) {
            // Use provided path (for testing)
            return testSuiteRepoPath;
        }
        return setupGitRepository(testSuiteRepoUrl, createTemporaryDirectory("quarkus-qe-test-suite"), "Test Suite");
    }

    /**
     * Set up a git repository (clone or update).
     * For Quarkus: uses depth-based clone, then incrementally deepens until reaching target date
     * For Test Suite: uses depth=1 clone (we only need current state to run tests, no history)
     */
    private Path setupGitRepository(String repoUrl, Path repoPath, String repoName) {
        boolean isQuarkusRepo = repoUrl.contains("quarkusio/quarkus");

        if (Files.exists(repoPath.resolve(".git"))) {
            logger.info("Updating existing " + repoName + " repository at: " + repoPath);

            if (isQuarkusRepo) {
                // For existing repo, just fetch and deepen as needed
                runCommand(repoPath, "git", "fetch", "origin", "main");
                runCommand(repoPath, "git", "checkout", "main");
                runCommand(repoPath, "git", "reset", "--hard", "origin/main");

                // Deepen until we reach the target date
                Instant targetDate = calculateShallowSince();
                deepenUntilDate(repoPath, targetDate);
            } else {
                // For test suite, just fetch main
                runCommand(repoPath, "git", "fetch", "origin", "main");
                runCommand(repoPath, "git", "checkout", "main");
                runCommand(repoPath, "git", "reset", "--hard", "origin/main");
            }
        } else {
            logger.info("Cloning " + repoName + " repository to: " + repoPath);
            try {
                Files.createDirectories(repoPath.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory: " + repoPath.getParent(), e);
            }

            if (isQuarkusRepo) {
                // Clone with depth 1 first (most reliable)
                logger.info("Cloning with depth 1 (latest commit only)");
                runCommand(repoPath.getParent(), "git", "clone",
                        "--depth=1",
                        "--single-branch", "--branch=main",
                        repoUrl, repoPath.getFileName().toString());

                // Then deepen until we reach the target date
                Instant targetDate = calculateShallowSince();
                logger.info("Target date for history: " + targetDate + " (" + lookbackDays + " days back)");
                deepenUntilDate(repoPath, targetDate);
            } else {
                // Shallow clone of just main branch (for test suite)
                // We only need the latest state to run tests, no history needed
                logger.info("Shallow cloning main branch only (depth 1)");
                runCommand(repoPath.getParent(), "git", "clone",
                        "--depth=1", "--single-branch", "--branch=main",
                        repoUrl, repoPath.getFileName().toString());
            }
        }

        return repoPath;
    }

    /**
     * Incrementally deepen a shallow clone until we have commits going back to the target date.
     * Uses git fetch --deepen to avoid network issues with large clones.
     *
     * Logic:
     * 1. Clone with depth=1 (just latest commit)
     * 2. Deepen by 10 commits at a time
     * 3. Check if we got new commits (compare commit count)
     * 4. Check if oldest merge commit is older than target date
     * 5. Stop when: no new commits OR oldest merge is old enough
     *
     * Note: --deepen=10 doesn't add exactly 10 commits. It deepens the shallow boundary,
     * which may add more commits if there's complex branching. We stop as soon as we reach
     * the target date, so overfetching is acceptable.
     *
     * Only checks merge commits for date comparison since individual commits may have
     * old dates from when they were created in PRs, not when they were merged.
     */
    private void deepenUntilDate(Path repoPath, Instant targetDate) {
        int iterations = 0;
        int maxIterations = 100; // Safety limit
        int previousCommitCount = 0;

        while (iterations < maxIterations) {
            // Deepen to get more commits - specify 'origin main' to deepen that specific branch
            logger.info("Deepening clone (iteration " + (iterations + 1) + ")");
            runCommand(repoPath, "git", "fetch", "--deepen=10", "origin", "main");
            iterations++;

            // Count total commits on main branch (first-parent only for accurate count)
            String countOutput = runCommand(repoPath, "git", "rev-list", "--count", "--first-parent", "HEAD");
            int currentCommitCount = Integer.parseInt(countOutput.trim());
            logger.info("  Main branch now has " + currentCommitCount + " commits");

            // Check if we got new commits
            if (currentCommitCount == previousCommitCount) {
                logger.info("  No new commits fetched - reached end of repository history");
                break;
            }
            previousCommitCount = currentCommitCount;

            // Get the oldest MERGE commit date (first-parent only to follow main branch)
            Instant oldestMergeDate = getOldestCommitDate(repoPath);
            if (oldestMergeDate != null) {
                logger.info("  Oldest merge commit: " + oldestMergeDate);

                // Check if all merge commits are older than target date
                if (oldestMergeDate.isBefore(targetDate) || oldestMergeDate.equals(targetDate)) {
                    logger.info("  Reached target date (" + targetDate + ")");
                    break;
                }
            } else {
                logger.info("  No merge commits found yet (deepening further)");
            }
        }

        if (iterations >= maxIterations) {
            logger.error("Reached maximum deepen iterations (" + maxIterations + "), stopping");
        }
    }

    /**
     * Get the date of the oldest MERGE commit in the current repository.
     * Only merge commits are considered because individual commits may have been
     * created weeks ago but only merged recently. The merge date is what matters
     * for determining the actual timeline of changes.
     *
     * Example scenario:
     * - Developer creates commit on Jan 1 in a PR
     * - PR sits in review for 2 weeks
     * - Merged to main on Jan 15
     * - Commit keeps Jan 1 date, but merge commit has Jan 15 date
     * - We need Jan 15 (merge date) not Jan 1 (commit date)
     *
     * Returns null if unable to determine.
     */
    private Instant getOldestCommitDate(Path repoPath) {
        try {
            // Get ALL merge commit dates on main branch
            // --first-parent: only follow the first parent (main branch history)
            // --merges: only show merge commits
            // --format=%cI: committer date in ISO format (one per line)
            String output = runCommand(repoPath, "git", "log",
                    "--first-parent", "--merges", "--format=%cI", "HEAD");

            if (output.trim().isEmpty()) {
                return null;
            }

            // Parse all dates and find the oldest (minimum)
            String[] dates = output.trim().split("\n");
            Instant oldest = null;
            for (String dateStr : dates) {
                if (!dateStr.trim().isEmpty()) {
                    Instant date = Instant.parse(dateStr.trim());
                    if (oldest == null || date.isBefore(oldest)) {
                        oldest = date;
                    }
                }
            }

            return oldest;
        } catch (Exception e) {
            logger.error("Failed to get oldest commit date: " + e.getMessage());
            return null;
        }
    }

    /**
     * Calculate the instant to use for cloning history.
     * For cloning, we need to go back far enough to cover BOTH:
     * - The configured lookback period (e.g., 5 days)
     * - The last run time (to have commits since then)
     * Use whichever is OLDER (further back in time) to ensure we have enough history.
     */
    private Instant calculateShallowSince() {
        // Use 'from' if configured, otherwise use current time (for tests)
        Instant referenceTime = from != null ? from : Instant.now();
        Instant configuredLookback = referenceTime.minus(Duration.ofDays(lookbackDays));

        if (previousHistory.lastRun() != null) {
            // Use whichever is OLDER (further back) to ensure we have enough history for bisecting
            return configuredLookback.isBefore(previousHistory.lastRun())
                    ? configuredLookback
                    : previousHistory.lastRun();
        }

        return configuredLookback;
    }

    /**
     * Get commits to test for bisecting.
     * <p>
     * If no previous history: Gets all commits from the lookback period (e.g., last 5 days)
     * If previous history exists: Gets commits from last tested commit to HEAD
     * <p>
     * This is the range ALL NEW failures will be tested against.
     * Logic: If a test was passing in the last run and failing now,
     * it must have broken somewhere in the new commits since then.
     */
    private List<String> getUntestedCommits() {
        String lastTestedCommit = previousHistory.quarkusCommit();

        List<String> commits = new ArrayList<>();

        if (lastTestedCommit == null) {
            // First run - get all commits from the lookback period
            Instant targetDate = calculateShallowSince();
            logger.info("No previous history, getting commits since " + targetDate + " (" + lookbackDays + " days back)");

            // Get all commits since the target date (the repo was already deepened to this date)
            String commitList = runCommand(quarkusRepo, "git", "rev-list",
                    "--first-parent", "--since=" + targetDate.toString(), "HEAD");

            for (String commit : commitList.split("\n")) {
                commit = commit.trim();
                if (!commit.isEmpty()) {
                    commits.add(commit);
                }
            }
        } else {
            // Get commits from last tested to HEAD
            logger.info("Getting commits from " + lastTestedCommit + " to HEAD");

            // Check if last tested commit exists in current repo
            try {
                runCommand(quarkusRepo, "git", "cat-file", "-e", lastTestedCommit);
            } catch (Exception e) {
                // Last tested commit not in repo - fall back to using lookback period
                Instant targetDate = calculateShallowSince();
                logger.error("Last tested commit " + lastTestedCommit + " not found in repo, getting commits since " + targetDate);
                String commitList = runCommand(quarkusRepo, "git", "rev-list",
                        "--first-parent", "--since=" + targetDate.toString(), "HEAD");
                for (String commit : commitList.split("\n")) {
                    commit = commit.trim();
                    if (!commit.isEmpty()) {
                        commits.add(commit);
                    }
                }
                return commits;
            }

            // Get commits between last tested and HEAD (first-parent only)
            String commitList = runCommand(quarkusRepo, "git", "rev-list",
                    "--first-parent",
                    lastTestedCommit + "..HEAD");

            for (String commit : commitList.split("\n")) {
                commit = commit.trim();
                if (!commit.isEmpty()) {
                    commits.add(commit);
                }
            }
        }

        logger.info("Found " + commits.size() + " commits to test");
        return commits;
    }

    /**
     * Bisect a failure to find the commit that introduced it.
     * Chooses between binary and linear search based on configuration.
     */
    private BisectResult bisectFailure(Failure failure, List<String> commitsToTest) {
        if (bisectStrategy == AppConfig.BisectStrategy.BINARY) {
            logger.info("Using BINARY search strategy for bisect");
            return bisectFailureBinary(failure, commitsToTest);
        } else {
            logger.info("Using LINEAR search strategy for bisect");
            return bisectFailureLinear(failure, commitsToTest);
        }
    }

    /**
     * Linear search from oldest to newest commit.
     * Slower but predictable - tests every commit in order.
     */
    private BisectResult bisectFailureLinear(Failure failure, List<String> commitsToTest) {
        List<String> testedCommits = new ArrayList<>();

        if (commitsToTest.isEmpty()) {
            logger.info("No commits to test for bisect");
            return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.CANNOT_REPRODUCE);
        }

        // Start from the oldest commit (last in list) and work forward
        for (int i = commitsToTest.size() - 1; i >= 0; i--) {
            String commit = commitsToTest.get(i);
            boolean isOldestCommit = (i == commitsToTest.size() - 1);
            logger.info("Testing commit " + (commitsToTest.size() - i) + "/" + commitsToTest.size() + ": " + commit);

            // Checkout commit
            runCommand(quarkusRepo, "git", "checkout", commit);
            testedCommits.add(commit);

            // Build Quarkus
            boolean buildSuccess = buildQuarkus(commit);
            if (!buildSuccess) {
                logger.info("Build failed for commit " + commit + ", skipping");
                // If the oldest commit fails to build, we cannot establish a baseline
                if (isOldestCommit) {
                    logger.error("Oldest commit failed to build - cannot establish baseline");
                    logger.error("The failure may have been introduced before our lookback range, or there's a build issue");
                    logger.error("Consider increasing the lookback period or checking commits before " + commit);
                    return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.BUILD_FAILED);
                }
                continue;
            }

            // Run the test
            boolean testPassed = runTest(failure);

            if (!testPassed) {
                // If this is the oldest commit, and it fails, we cannot determine when failure was introduced
                if (isOldestCommit) {
                    logger.error("Test FAILED at oldest commit: " + commit);
                    logger.error("Cannot determine failure-introducing commit - failure exists at oldest commit in range");
                    logger.error("The failure was likely introduced BEFORE our lookback range");
                    logger.error("Consider increasing the lookback period or checking commits before " + commit);
                    return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.OLDEST_COMMIT_FAILED);
                }

                // Found the first failing commit
                logger.info("Test failed at commit: " + commit);
                String pullRequest = findPullRequest(commit);
                String commitMessage = getCommitMessage(quarkusRepo, commit);
                return new BisectResult(commit, pullRequest, commitMessage, testedCommits, RootCause.FailureReason.FOUND);
            }

            logger.info("Test passed at commit: " + commit);
        }

        logger.info("Cannot reproduce the failure - test passes on all commits (oldest to newest)");
        logger.info("This indicates the test is flaky or has environmental dependencies");
        return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.CANNOT_REPRODUCE);
    }

    /**
     * Binary search to find the first failing commit.
     * Significantly faster than linear - O(log n) instead of O(n).
     * Falls back to linear search if too many build failures occur.
     */
    private BisectResult bisectFailureBinary(Failure failure, List<String> commitsToTest) {
        List<String> testedCommits = new ArrayList<>();
        int buildFailureCount = 0;
        final int MAX_BUILD_FAILURES = 3; // Fall back to linear after 3 build failures

        if (commitsToTest.isEmpty()) {
            logger.info("No commits to test for bisect");
            return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.CANNOT_REPRODUCE);
        }

        // Binary search: low = oldest (should pass), high = newest (should fail)
        // Note: commitsToTest are in reverse chronological order (newest first)
        int low = commitsToTest.size() - 1;  // oldest commit index
        int high = 0;  // newest commit index

        // Track test results to avoid retesting the same commit
        Map<String, Boolean> commitTestResults = new HashMap<>();

        logger.info("Binary search range: " + commitsToTest.size() + " commits");

        // VALIDATION: Test the oldest commit first to ensure we have a known-good baseline
        // If the oldest commit in our range also fails, we cannot determine when the failure
        // was introduced - it could be from before our lookback range
        logger.info("Validating oldest commit in range to establish known-good baseline");
        String oldestCommit = commitsToTest.get(low);
        logger.info("Testing oldest commit at index " + low + ": " + oldestCommit);

        runCommand(quarkusRepo, "git", "checkout", oldestCommit);
        testedCommits.add(oldestCommit);

        boolean oldestBuildSuccess = buildQuarkus(oldestCommit);
        if (!oldestBuildSuccess) {
            logger.error("Oldest commit failed to build - cannot establish baseline");
            logger.error("The failure may have been introduced before our lookback range, or there's a build issue");
            logger.error("Consider increasing the lookback period or checking commits before " + oldestCommit);
            return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.BUILD_FAILED);
        }

        boolean oldestTestPassed = runTest(failure);
        if (!oldestTestPassed) {
            logger.error("Test FAILED at oldest commit: " + oldestCommit);
            logger.error("Cannot determine failure-introducing commit - failure exists at oldest commit in range");
            logger.error("The failure was likely introduced BEFORE our lookback range");
            logger.error("Consider increasing the lookback period or checking commits before " + oldestCommit);
            return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.OLDEST_COMMIT_FAILED);
        }

        logger.info("Test PASSED at oldest commit - proceeding with bisect");
        commitTestResults.put(oldestCommit, true); // Cache the result

        while (low > high) {
            int mid = high + (low - high) / 2;
            String commit = commitsToTest.get(mid);

            // Check if we already tested this commit
            if (commitTestResults.containsKey(commit)) {
                logger.info("Commit " + commit + " at index " + mid + " already tested, using cached result");
                boolean testPassed = commitTestResults.get(commit);

                if (testPassed) {
                    logger.info("Test PASSED at commit: " + commit);
                    low = mid - 1;
                } else {
                    logger.info("Test FAILED at commit: " + commit);

                    if (mid == low) {
                        logger.info("Found first failing commit: " + commit);
                        String pullRequest = findPullRequest(commit);
                        String commitMessage = getCommitMessage(quarkusRepo, commit);
                        return new BisectResult(commit, pullRequest, commitMessage, testedCommits, RootCause.FailureReason.FOUND);
                    }

                    if (mid == high) {
                        high = mid + 1;
                    } else {
                        high = mid;
                    }
                }
                continue;
            }

            logger.info("Binary search: testing commit at index " + mid + " (range: " + high + "-" + low + "): " + commit);

            // Checkout commit
            runCommand(quarkusRepo, "git", "checkout", commit);
            testedCommits.add(commit);

            // Build Quarkus
            boolean buildSuccess = buildQuarkus(commit);
            if (!buildSuccess) {
                logger.info("Build failed for commit " + commit);
                buildFailureCount++;

                if (buildFailureCount >= MAX_BUILD_FAILURES) {
                    logger.info("Too many build failures (" + buildFailureCount + "), falling back to LINEAR search");
                    return bisectFailureLinear(failure, commitsToTest);
                }

                // Try to work around build failure by testing adjacent commit
                // If we're searching in the "newer" half, try one commit older
                // If we're searching in the "older" half, try one commit newer
                if (mid > (high + low) / 2 && mid < commitsToTest.size() - 1) {
                    // Try older commit (mid + 1)
                    logger.info("Trying adjacent older commit...");
                    mid = mid + 1;
                } else if (mid > high) {
                    // Try newer commit (mid - 1)
                    logger.info("Trying adjacent newer commit...");
                    mid = mid - 1;
                } else {
                    // Can't work around, narrow the search range and continue
                    logger.info("Skipping unbuildable commit, narrowing search range");
                    low = mid - 1;
                    continue;
                }

                commit = commitsToTest.get(mid);
                logger.info("Testing adjacent commit at index " + mid + ": " + commit);
                runCommand(quarkusRepo, "git", "checkout", commit);
                testedCommits.add(commit);

                buildSuccess = buildQuarkus(commit);
                if (!buildSuccess) {
                    logger.info("Adjacent commit also failed to build, narrowing range");
                    low = mid - 1;
                    continue;
                }
            }

            // Run the test
            boolean testPassed = runTest(failure);

            // Cache the test result to avoid retesting
            commitTestResults.put(commit, testPassed);

            if (testPassed) {
                // Test passed, so failure is in newer commits (lower indices)
                logger.info("Test PASSED at commit: " + commit);
                low = mid - 1;
            } else {
                // Test failed, so this could be the culprit or it's in older commits (higher indices)
                logger.info("Test FAILED at commit: " + commit);

                if (mid == low) {
                    // Found the first failing commit (at oldest boundary)
                    logger.info("Found first failing commit: " + commit);
                    String pullRequest = findPullRequest(commit);
                    String commitMessage = getCommitMessage(quarkusRepo, commit);
                    return new BisectResult(commit, pullRequest, commitMessage, testedCommits, RootCause.FailureReason.FOUND);
                }

                if (mid == high) {
                    // At newest boundary, need to check if older commits also fail
                    // Move high forward to continue binary search
                    high = mid + 1;
                } else {
                    high = mid;
                }
            }
        }

        // If we exit the loop, low == high, test that commit
        if (low >= 0 && low < commitsToTest.size()) {
            String commit = commitsToTest.get(low);
            logger.info("Final commit to test at index " + low + ": " + commit);

            runCommand(quarkusRepo, "git", "checkout", commit);
            testedCommits.add(commit);

            boolean buildSuccess = buildQuarkus(commit);
            if (!buildSuccess) {
                logger.error("Build failed for commit " + commit + " - cannot complete bisect");
                logger.error("This may indicate a build issue in Quarkus main branch at this commit");
                logger.error("Check https://github.com/quarkusio/quarkus/commit/" + commit);
                logger.error("Bisect incomplete due to build failure");
                logger.info("Tested commits: " + String.join(", ", testedCommits));
                return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.BUILD_FAILED);
            } else {
                boolean testPassed = runTest(failure);
                if (!testPassed) {
                    logger.info("Found first failing commit: " + commit);
                    String pullRequest = findPullRequest(commit);
                    String commitMessage = getCommitMessage(quarkusRepo, commit);
                    return new BisectResult(commit, pullRequest, commitMessage, testedCommits, RootCause.FailureReason.FOUND);
                }
            }
        }

        logger.info("Cannot reproduce the failure - test passes on all tested commits (oldest to newest)");
        logger.info("This indicates the test is flaky or has environmental dependencies");
        logger.info("Tested commits: " + String.join(", ", testedCommits));
        return new BisectResult(null, null, null, testedCommits, RootCause.FailureReason.CANNOT_REPRODUCE);
    }

    /**
     * Build Quarkus with quick profile.
     */
    protected boolean buildQuarkus(String commit) {
        logger.info("Building Quarkus with 'MAVEN_OPTS=\"-Xmx4g\" ./mvnw -Dquickly' (as per CONTRIBUTING.md)");

        ProcessBuilder pb = new ProcessBuilder("./mvnw", "-Dquickly");
        pb.directory(quarkusRepo.toFile());
        pb.redirectErrorStream(true);
        // Set MAVEN_OPTS as recommended in https://github.com/quarkusio/quarkus/blob/main/CONTRIBUTING.md
        pb.environment().put("MAVEN_OPTS", "-Xmx4g");

        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            boolean success = (exitCode == 0);

            if (!success) {
                // Save full build log to file for debugging
                String logFileName = "quarkus-build-failed-" + commit.substring(0, 8) + ".log";
                Path logFile = Paths.get(logFileName);
                try {
                    Files.writeString(logFile, output.toString());
                    logger.error("Full build log saved to: " + logFile.toAbsolutePath());
                } catch (IOException e) {
                    logger.error("Failed to save build log: " + e.getMessage());
                }

                // Extract and log the actual error
                logger.error("============ BUILD FAILED (exit code: " + exitCode + ") ============");

                String[] lines = output.toString().split("\n");

                // Strategy: Find "[ERROR]" lines and show context around them
                // Maven typically shows errors with [ERROR] prefix
                List<Integer> errorLineIndices = new ArrayList<>();
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].contains("[ERROR]")) {
                        errorLineIndices.add(i);
                    }
                }

                if (!errorLineIndices.isEmpty()) {
                    // Show all [ERROR] lines with 2 lines of context before and after
                    logger.error("Maven errors:");
                    for (Integer errorIndex : errorLineIndices) {
                        int start = Math.max(0, errorIndex - 2);
                        int end = Math.min(lines.length - 1, errorIndex + 2);
                        for (int i = start; i <= end; i++) {
                            logger.error(lines[i]);
                        }
                    }
                } else {
                    // No [ERROR] lines found, show last 50 lines
                    logger.error("No [ERROR] markers found in Maven output. Last 50 lines:");
                    int startLine = Math.max(0, lines.length - 50);
                    for (int i = startLine; i < lines.length; i++) {
                        logger.error(lines[i]);
                    }
                }
                logger.error("======================================");
            }
            return success;
        } catch (IOException | InterruptedException e) {
            logger.error("Build command failed to execute: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Run a specific test against the built Quarkus.
     */
    protected boolean runTest(Failure failure) {
        // Extract test class simple name (outside try for use in catch block)
        String testClass = failure.testClassName();
        int lastDot = testClass.lastIndexOf('.');
        String simpleClassName = lastDot >= 0 ? testClass.substring(lastDot + 1) : testClass;

        try {

            // Extract module path from the failure
            // The modulePath is the absolute path to the module, we need relative path
            String moduleRelativePath = extractModuleRelativePath(failure);

            // Detect if this is a native mode test by checking if "native" appears in the module path
            // Artifact names follow pattern: artifacts-native21-... or artifacts-jvm21-...
            boolean isNativeTest = failure.modulePath().toLowerCase().contains("native");
            String testMode = isNativeTest ? "NATIVE" : "JVM";

            logger.info("Running test " + simpleClassName + " in module " + moduleRelativePath + " [" + testMode + " mode]");

            // Get the Quarkus version that was built
            String quarkusVersion = getQuarkusVersion();
            logger.info("Using Quarkus version: " + quarkusVersion);

            // Build Maven command arguments (matching quarkus-test-suite daily build)
            List<String> mvnArgs = new ArrayList<>();
            mvnArgs.add("mvn");
            mvnArgs.add("-fae"); // fail at end
            mvnArgs.add("-V"); // show version
            mvnArgs.add("-B"); // batch mode (non-interactive)
            mvnArgs.add("--no-transfer-progress"); // don't show download progress
            mvnArgs.add("clean");
            mvnArgs.add("verify");
            mvnArgs.add("-Dit.test=" + simpleClassName);
            mvnArgs.add("-Dquarkus.platform.version=" + quarkusVersion);

            // Always add Quarkus CLI test args (needed for CLI-related tests)
            mvnArgs.add("-Dinclude.quarkus-cli-tests");
            // The quarkus-dev-cli script is created in the current working directory by the workflow
            // Use absolute path from current working directory, not from test suite parent
            Path quarkusCliPath = Paths.get("quarkus-dev-cli").toAbsolutePath();
            mvnArgs.add("-Dts.quarkus.cli.cmd=" + quarkusCliPath);

            // Add native-specific args if this is a native test
            if (isNativeTest) {
                mvnArgs.add("-Dnative");
                mvnArgs.add("-Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21");
            }

            mvnArgs.add("-f");
            mvnArgs.add(moduleRelativePath);

            logger.info("Executing: " + String.join(" ", mvnArgs));

            String output = runCommand(testSuiteRepo, mvnArgs.toArray(new String[0]));

            // Check if test passed
            boolean testPassed = output.contains("BUILD SUCCESS") && !output.contains("Failures: 0, Errors: 0");

            if (!testPassed) {
                logger.info("Test FAILED");

                // Save full test output to file for debugging
                String logFileName = "test-failed-" + simpleClassName + "-" +
                    System.currentTimeMillis() + ".log";
                Path logFile = Paths.get(logFileName);
                try {
                    Files.writeString(logFile, output);
                    logger.info("Full test log saved to: " + logFile.toAbsolutePath());
                } catch (IOException e) {
                    logger.error("Failed to save test log: " + e.getMessage());
                }

                // Extract and log relevant failure information
                logger.info("============ TEST FAILURE DETAILS ============");
                String failureDetails = extractTestFailureDetails(output);
                logger.info(failureDetails);
                logger.info("==============================================");
            } else {
                logger.info("Test PASSED");
            }

            return testPassed;

        } catch (CommandFailureException e) {
            logger.error("Test execution failed: " + e.getMessage());

            // Save full test output to file for debugging
            String logFileName = "test-failed-" + simpleClassName + "-" +
                System.currentTimeMillis() + ".log";
            Path logFile = Paths.get(logFileName);
            try {
                Files.writeString(logFile, e.getOutput());
                logger.info("Full test log saved to: " + logFile.toAbsolutePath());
            } catch (IOException ioEx) {
                logger.error("Failed to save test log: " + ioEx.getMessage());
            }

            // Extract and log relevant failure information
            logger.info("============ TEST FAILURE DETAILS ============");
            String failureDetails = extractTestFailureDetails(e.getOutput());
            logger.info(failureDetails);
            logger.info("==============================================");

            return false;
        } catch (Exception e) {
            logger.error("Test execution failed: " + e.getMessage());
            // If execution fails, assume test failed
            return false;
        }
    }

    /**
     * Extract relevant test failure details from Maven output.
     * Looks for test execution results and failure markers rather than just the last N lines.
     */
    private String extractTestFailureDetails(String output) {
        String[] lines = output.split("\n");
        StringBuilder details = new StringBuilder();

        // Look for test result lines (e.g., "Tests run: 1, Failures: 1, Errors: 0, Skipped: 0")
        // and failure markers (e.g., "<<< FAILURE!" or "<<< ERROR!")
        int foundRelevantLines = 0;
        final int maxLines = 50; // Show up to 50 relevant lines

        for (int i = 0; i < lines.length && foundRelevantLines < maxLines; i++) {
            String line = lines[i];

            // Check if this line is relevant
            boolean isRelevant = line.contains("Tests run:")
                || line.contains("<<< FAILURE!")
                || line.contains("<<< ERROR!")
                || line.contains("BUILD FAILURE")
                || line.contains("Failed to execute goal")
                || line.contains("[ERROR]")
                || (line.contains("[INFO]") && line.contains("FAILURE"));

            if (isRelevant) {
                // Include this line and some context around it
                int contextStart = Math.max(0, i - 2);
                int contextEnd = Math.min(lines.length - 1, i + 5);

                for (int j = contextStart; j <= contextEnd && foundRelevantLines < maxLines; j++) {
                    if (j >= i || foundRelevantLines == 0) { // Avoid duplicate context
                        details.append(lines[j]).append("\n");
                        foundRelevantLines++;
                    }
                }
                i = contextEnd; // Skip ahead to avoid duplicates
            }
        }

        // If we didn't find specific failure patterns, fall back to last 30 lines
        if (foundRelevantLines == 0) {
            int startLine = Math.max(0, lines.length - 30);
            for (int i = startLine; i < lines.length; i++) {
                details.append(lines[i]).append("\n");
            }
        }

        return details.toString();
    }

    /**
     * Extract the relative module path from the failure.
     * For example, if modulePath is "/bla/bla/quarkus-test-suite/a/b/c/d/e/target/failsafe-reports",
     * return "a/b/c/d/e"
     *
     * We find the test suite project name in the path and extract everything after it.
     */
    private String extractModuleRelativePath(Failure failure) {
        String absolutePath = failure.modulePath();

        // Strip /target/failsafe-reports or /failsafe-reports suffix
        String pathWithoutSuffix = absolutePath;
        if (pathWithoutSuffix.endsWith("/target/failsafe-reports")) {
            pathWithoutSuffix = pathWithoutSuffix.substring(0,
                pathWithoutSuffix.length() - "/target/failsafe-reports".length());
        } else if (pathWithoutSuffix.endsWith("/failsafe-reports")) {
            pathWithoutSuffix = pathWithoutSuffix.substring(0,
                pathWithoutSuffix.length() - "/failsafe-reports".length());
        } else if (pathWithoutSuffix.endsWith("/target")) {
            pathWithoutSuffix = pathWithoutSuffix.substring(0,
                pathWithoutSuffix.length() - "/target".length());
        }

        // Look for test suite project name in the path (e.g., quarkus-test-suite, quarkus-qe-test-suite, quarkus-test-framework)
        // and extract everything after it
        String[] projectNames = {"quarkus-test-suite", "quarkus-test-framework"};
        for (String projectName : projectNames) {
            int index = pathWithoutSuffix.indexOf("/" + projectName + "/");
            if (index >= 0) {
                // Found the project name, extract everything after it
                String afterProject = pathWithoutSuffix.substring(index + projectName.length() + 2);
                return afterProject;
            }
        }

        // Special handling for GitHub artifacts temp directories (github-artifacts-<uuid>)
        // The structure is: /tmp/github-artifacts-<uuid>/artifacts-<platform>-<runtime>-<modules>/actual/module/path
        // We need to skip both the github-artifacts directory AND the artifacts-* directory
        String[] parts = pathWithoutSuffix.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].startsWith("github-artifacts-")) {
                // Found the temp artifacts directory
                // The next part should be the artifact name (artifacts-linux-jvm21-rootmodules, etc.)
                // We need to skip that and extract everything after it
                int startIndex = i + 1; // Start after github-artifacts-<uuid>

                // Skip the artifact directory if it starts with "artifacts-"
                if (startIndex < parts.length && parts[startIndex].startsWith("artifacts-")) {
                    startIndex++; // Skip the artifacts-* directory
                }

                // Extract the actual module path
                StringBuilder modulePathBuilder = new StringBuilder();
                for (int j = startIndex; j < parts.length; j++) {
                    if (!modulePathBuilder.isEmpty()) {
                        modulePathBuilder.append("/");
                    }
                    modulePathBuilder.append(parts[j]);
                }
                String modulePath = modulePathBuilder.toString();
                logger.info("Extracted module path from GitHub artifacts: " + modulePath);
                return modulePath;
            }
        }

        // Fallback: if no project name found
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }
        String moduleRelativePath = parts[parts.length - 1];
        logger.info("Failed to identify project name (maybe this project is not supported yet), "
                + "guessing test relative path as " + moduleRelativePath);

        return moduleRelativePath;
    }

    /**
     * Get the Quarkus version from the built repository.
     */
    private String getQuarkusVersion() {
        try {
            // Read version from pom.xml
            String pomContent = runCommand(quarkusRepo, "mvn", "help:evaluate",
                    "-Dexpression=project.version", "-q", "-DforceStdout");
            return pomContent.trim();
        } catch (Exception e) {
            logger.error("Failed to get Quarkus version: " + e.getMessage());
            // Fallback to a default version pattern
            return "999-SNAPSHOT";
        }
    }

    /**
     * Find the PR associated with a commit.
     */
    private String findPullRequest(String commit) {
        try {
            String commitMessage = getCommitMessage(quarkusRepo, commit);
            if (commitMessage == null) {
                return null;
            }
            Pattern prPattern = Pattern.compile("#(\\d+)");
            Matcher matcher = prPattern.matcher(commitMessage);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            logger.debug("Failed to find PR for commit: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get the commit message for a commit.
     */
    private String getCommitMessage(Path repoPath, String commit) {
        try {
            return runCommand(repoPath, "git", "log", "--format=%B", "-n", "1", commit).trim();
        } catch (Exception e) {
            logger.debug("Failed to get commit message: " + e.getMessage());
            return null;
        }
    }

    /**
     * Run a command and return output.
     */
    private String runCommand(Path workingDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Create exception that includes the output
                CommandFailureException ex = new CommandFailureException(
                    "Command failed with exit code " + exitCode + ": " + String.join(" ", command),
                    output.toString()
                );
                throw ex;
            }

            return output.toString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to execute command: " + String.join(" ", command), e);
        }
    }

    /**
     * Exception that includes command output for better error reporting.
     */
    private static class CommandFailureException extends RuntimeException {
        private final String output;

        CommandFailureException(String message, String output) {
            super(message);
            this.output = output;
        }

        String getOutput() {
            return output;
        }
    }

    /**
     * Result of git bisect operation.
     */
    private record BisectResult(String commit, String pullRequest, String commitMessage, List<String> testedCommits, RootCause.FailureReason reason) {
        boolean foundCommit() {
            return commit != null;
        }
    }

    private static Path createTemporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

