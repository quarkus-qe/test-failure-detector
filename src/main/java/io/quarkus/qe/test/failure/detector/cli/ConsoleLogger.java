package io.quarkus.qe.test.failure.detector.cli;

import jakarta.inject.Singleton;

import java.io.PrintWriter;

@Singleton
public class ConsoleLogger {

    private PrintWriter stdOutWriter = null;
    private PrintWriter stdErrWriter = null;

    void setWriters(PrintWriter stdOutWriter, PrintWriter stdErrWriter) {
        this.stdOutWriter = stdOutWriter;
        this.stdErrWriter = stdErrWriter;
    }

    public void info(String logMessage) {
        stdOutWriter.println(logMessage);
    }

    public void error(String logMessage) {
        stdErrWriter.println(logMessage);
    }
}
