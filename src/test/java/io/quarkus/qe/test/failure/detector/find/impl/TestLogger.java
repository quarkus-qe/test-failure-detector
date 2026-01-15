package io.quarkus.qe.test.failure.detector.find.impl;

import io.quarkus.qe.test.failure.detector.logger.Logger;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@ApplicationScoped
public class TestLogger implements Logger {

    @Override
    public void info(String logMessage) {
        System.out.println("INFO: " + logMessage);
    }

    @Override
    public void error(String logMessage) {
        System.err.println("ERROR: " + logMessage);
    }
}
