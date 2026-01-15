package io.quarkus.qe.test.failure.detector.project.impl;

import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.find.FailuresFinder;
import io.quarkus.qe.test.failure.detector.find.impl.TestLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that FailuresFinder can handle the artifact structure
 * that would be downloaded by the download-github-artifacts.sh script.
 */
@QuarkusTest
@TestProfile(GitHubWorkflowProjectSourceTest.MockScriptProfile.class)
class GitHubWorkflowProjectSourceTest {

    @Inject
    FailuresFinder failuresFinder;

    public static class MockScriptProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(TestLogger.class);
        }
    }

    @Test
    void testFailuresFinderHandlesGitHubArtifactStructure() throws URISyntaxException {
        // Use the mock artifact structure that represents downloaded GitHub artifacts
        Path artifactsDir = getTestResourcePath("github-artifacts/mock-artifact");

        // Use FailuresFinder to parse the artifacts
        Collection<Failure> failures = failuresFinder.find(artifactsDir);

        // Verify we found the expected failure
        assertEquals(1, failures.size(), "Should find exactly one failure");

        Failure failure = failures.iterator().next();
        assertEquals("io.quarkus.ts.github.GitHubArtifactIT", failure.testClassName());
        assertEquals("testFromGitHub", failure.testMethodName());
        assertEquals("GitHub artifact test failed", failure.failureMessage());
        assertEquals("java.lang.AssertionError", failure.failureType());
        assertTrue(failure.testRunLog().contains("GitHubArtifactIT.java:30"));
    }

    private Path getTestResourcePath(String resourcePath) throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource(resourcePath).toURI());
    }
}

