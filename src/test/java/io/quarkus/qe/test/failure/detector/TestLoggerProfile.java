package io.quarkus.qe.test.failure.detector;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Set;

public class TestLoggerProfile implements QuarkusTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(TestLogger.class);
    }
}
