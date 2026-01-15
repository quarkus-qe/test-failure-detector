package io.quarkus.qe.test.failure.detector.project.impl;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;

import java.nio.file.Path;

@Unremovable
@Dependent
public final class GitHubWorkflowProjectSource {

    public Path getProjectFailuresDir(String workflowLink) {
        // FIXME: impl. me!
        return Path.of(".");
    }

}
