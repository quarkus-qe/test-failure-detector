package io.quarkus.qe.test.failure.detector.logger;

public interface Logger {

    void info(String logMessage);

    void error(String logMessage);

}
