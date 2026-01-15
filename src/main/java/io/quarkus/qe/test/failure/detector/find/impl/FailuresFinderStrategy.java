package io.quarkus.qe.test.failure.detector.find.impl;

import io.quarkus.qe.test.failure.detector.find.Failure;

import java.nio.file.Path;
import java.util.Collection;

interface FailuresFinderStrategy {

    Collection<Failure> find(Path testedProjectDir);

}
