package io.quarkus.qe.test.failure.detector.find;

public interface Failure {

    String testClassName();

    String testMethodName();

    /**
     * Path to the specific Maven module project root with the {@link #testClassName()} class.
     * If the {@link #testClassName()} is in some nested Maven module, this will point to the directory
     * with the "pom.xml".
     * <p />
     * Project root directory is "/tmp/my-project". There is a test module in "/tmp/my-project/module/sub/module",
     * which contains "pom.xml" file and the test class is
     * present at the "/tmp/my-project/module/sub/module/src/test/java/org/acme/TheTest.java".
     */
    String modulePath();

    String failureMessage();

    FailureType failureType();

    String throwableClass();

    String testRunLog();

    enum FailureType {
        FAILURE, ERROR
    }

}
