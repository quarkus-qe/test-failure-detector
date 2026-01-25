package io.quarkus.qe.test.failure.detector.find.impl;

import io.quarkus.qe.test.failure.detector.TestBeanProfile;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(TestBeanProfile.class)
class FailSafeFinderStrategyTest {

    @Inject
    FailSafeFinderStrategy strategy;

    @Test
    void testFindFailuresInSingleModule() throws URISyntaxException {
        Path testDir = getTestResourcePath("failsafe-reports/single-module");

        Collection<Failure> failures = strategy.find(testDir);

        assertEquals(1, failures.size());
        Failure failure = failures.iterator().next();

        assertEquals("io.quarkus.ts.example.GreetingResourceIT", failure.testClassName());
        assertEquals("testFailingEndpoint", failure.testMethodName());
        assertEquals("Expected status code <200> but was <500>.", failure.failureMessage());
        assertEquals(Failure.FailureType.FAILURE, failure.failureType());
        assertTrue(failure.testRunLog().contains("AssertionError"));
        assertTrue(failure.modulePath().endsWith("single-module"));
    }

    @Test
    void testFindFailuresInMultiModule() throws URISyntaxException {
        Path testDir = getTestResourcePath("failsafe-reports/multi-module");

        Collection<Failure> failures = strategy.find(testDir);

        assertEquals(2, failures.size());

        List<String> testClasses = failures.stream()
                .map(Failure::testClassName)
                .sorted()
                .toList();

        assertTrue(testClasses.contains("io.quarkus.ts.moduleA.ServiceAIT"));
        assertTrue(testClasses.contains("io.quarkus.ts.moduleB.ServiceBIT"));
    }

    @Test
    void testNoFailuresFound() throws URISyntaxException {
        Path testDir = getTestResourcePath("failsafe-reports/no-failures");

        Collection<Failure> failures = strategy.find(testDir);

        assertEquals(0, failures.size());
    }

    @Test
    void testBothErrorAndFailure() throws URISyntaxException {
        Path testDir = getTestResourcePath("failsafe-reports/error-and-failure");

        Collection<Failure> failures = strategy.find(testDir);

        assertEquals(2, failures.size());

        List<Failure.FailureType> failureTypes = failures.stream()
                .map(Failure::failureType)
                .sorted()
                .toList();

        assertTrue(failureTypes.contains(Failure.FailureType.FAILURE));
        assertTrue(failureTypes.contains(Failure.FailureType.ERROR));
    }

    private Path getTestResourcePath(String resourcePath) throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource(resourcePath).toURI());
    }
}
