package io.quarkus.qe.test.failure.detector.find;

import java.nio.file.Path;
import java.util.Collection;

public interface FailuresFinder {

    Collection<Failure> find(Path projectDirectory);

}
