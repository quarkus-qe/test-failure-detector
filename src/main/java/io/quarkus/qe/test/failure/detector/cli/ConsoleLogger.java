package io.quarkus.qe.test.failure.detector.cli;

import io.quarkus.qe.test.failure.detector.logger.Logger;
import jakarta.inject.Singleton;

import java.io.PrintWriter;

@Singleton
final class ConsoleLogger implements Logger {

    private PrintWriter stdOutWriter = null;
    private PrintWriter stdErrWriter = null;
    private boolean debug = false;

    void setWriters(PrintWriter stdOutWriter, PrintWriter stdErrWriter, boolean debug) {
        this.stdOutWriter = stdOutWriter;
        this.stdErrWriter = stdErrWriter;
        this.debug = debug;
    }

    public void info(String logMessage) {
        stdOutWriter.println(logMessage);
    }

    public void error(String logMessage) {
        stdErrWriter.println(logMessage);
    }

    @Override
    public void debug(String logMessage) {

    }
}
