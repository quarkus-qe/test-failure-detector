package io.quarkus.qe.test.failure.detector.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test git repository with merge commits (similar to Quarkus structure).
 * This test verifies the repository can be used for git bisect scenarios.
 *
 * The test repo has ~33 commits with merge commits from feature branches.
 * Values change at different points: A -> B -> C -> D
 */
// FIXME: this test doesn't make sense, but it should be transformed into using the command we are creating here
class GitBisectTest {

    @Test
    void testGitRepositoryStructure(@TempDir Path tempDir) throws Exception {
        // Copy the test git repository to a temporary directory
        Path testRepo = copyTestRepo(tempDir);

        // Verify git repository is valid
        assertTrue(Files.exists(testRepo.resolve(".git")), ".git directory should exist");

        // Get commit count
        String commitCount = runGitCommand(testRepo, "git", "rev-list", "--count", "HEAD").trim();
        assertEquals("33", commitCount, "Should have 33 commits");

        // Verify we have merge commits
        String mergeCommitCount = runGitCommand(testRepo, "git", "rev-list", "--merges", "--count", "HEAD").trim();
        assertTrue(Integer.parseInt(mergeCommitCount) > 5, "Should have multiple merge commits");

        // Print repository info
        System.out.println("Test repository has " + commitCount + " commits with " + mergeCommitCount + " merges");
    }

    @Test
    void testFindCommitsWithSpecificOutput(@TempDir Path tempDir) throws Exception {
        Path testRepo = copyTestRepo(tempDir);

        // Get all commits (first-parent only, like Quarkus)
        List<String> commits = getFirstParentCommits(testRepo);
        System.out.println("First-parent commits: " + commits.size());

        // Find commits with each output value
        String commitWithA = findCommitWithOutput(testRepo, commits, "A");
        String commitWithB = findCommitWithOutput(testRepo, commits, "B");
        String commitWithC = findCommitWithOutput(testRepo, commits, "C");
        String commitWithD = findCommitWithOutput(testRepo, commits, "D");

        // Verify we found all values
        assertNotNull(commitWithA, "Should find a commit printing A");
        assertNotNull(commitWithB, "Should find a commit printing B");
        assertNotNull(commitWithC, "Should find a commit printing C");
        assertNotNull(commitWithD, "Should find a commit printing D");

        System.out.println("Found commits:");
        System.out.println("  A: " + commitWithA);
        System.out.println("  B: " + commitWithB);
        System.out.println("  C: " + commitWithC);
        System.out.println("  D: " + commitWithD);

        // Verify D is the latest (HEAD)
        String headCommit = runGitCommand(testRepo, "git", "rev-parse", "HEAD").trim();
        assertEquals(headCommit, commitWithD, "HEAD should print D");
    }

    /**
     * Copy the test git repository from src/test/resources to a temporary directory.
     */
    private Path copyTestRepo(Path tempDir) throws Exception {
        Path testRepoSource = Paths.get("src/test/resources/git-bisect-test").toAbsolutePath();

        if (!Files.exists(testRepoSource)) {
            throw new IOException("Test repository not found at: " + testRepoSource);
        }

        Path testRepoTarget = tempDir.resolve("git-bisect-test");

        // Use cp -r to preserve the git repository structure including .git
        ProcessBuilder pb = new ProcessBuilder("cp", "-r",
                testRepoSource.toString(), tempDir.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Failed to copy test repository: " + output);
        }

        if (!Files.exists(testRepoTarget.resolve(".git"))) {
            throw new IOException(".git directory not found in copied repo: " + testRepoTarget);
        }

        return testRepoTarget;
    }

    /**
     * Get commits following first-parent (like --first-parent in git log).
     */
    private List<String> getFirstParentCommits(Path repo) throws Exception {
        String output = runGitCommand(repo, "git", "rev-list", "--first-parent", "HEAD");
        return List.of(output.split("\n"));
    }

    /**
     * Find a commit that outputs the specified value.
     */
    private String findCommitWithOutput(Path repo, List<String> commits, String targetOutput) throws Exception {
        // Sample some commits to find one with the target output
        // Check every 3rd commit to speed up the test
        for (int i = 0; i < commits.size(); i += 3) {
            String commit = commits.get(i).trim();
            String output = runMavenAndGetOutputAtCommit(repo, commit);
            if (targetOutput.equals(output)) {
                return commit;
            }
        }
        return null;
    }

    /**
     * Run Maven at a specific commit and get the output.
     */
    private String runMavenAndGetOutputAtCommit(Path repo, String commit) throws Exception {
        runGitCommand(repo, "git", "checkout", "-q", commit);

        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "compile", "exec:java");
            pb.directory(repo.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines()
                        .filter(line -> line.matches("^[A-D]$"))
                        .findFirst()
                        .orElse("");
            }

            process.waitFor();
            return output;
        } finally {
            runGitCommand(repo, "git", "checkout", "-q", "main");
        }
    }

    /**
     * Run a git command and return the output.
     */
    private String runGitCommand(Path repo, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repo.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 && !output.contains("bisect")) {
            throw new IOException("Git command failed: " + String.join(" ", command) + "\nOutput: " + output);
        }

        return output;
    }
}

