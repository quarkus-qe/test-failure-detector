package io.quarkus.qe.test.failure.detector.report;

public interface RootCauseReporter {

    void report(RootCauseReport rootCauseReport);

    RootCauseReportBuilder builder();

}
