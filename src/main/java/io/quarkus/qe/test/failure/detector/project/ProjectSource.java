package io.quarkus.qe.test.failure.detector.project;

import io.quarkus.arc.Arc;
import io.quarkus.qe.test.failure.detector.project.impl.GitHubWorkflowProjectSource;

import java.nio.file.Path;
import java.util.function.Function;

public enum ProjectSource {

    LOCAL_DIRECTORY(Path::of), GITHUB_ACTION_ARTIFACTS(workflowLink -> Arc.requireContainer()
            .select(GitHubWorkflowProjectSource.class).get().getProjectFailuresDir(workflowLink));

    private final Function<String, Path> argumentToPath;

    ProjectSource(Function<String, Path> argumentToPath) {
        this.argumentToPath = argumentToPath;
    }

    public Path getTestedProjectDirectory(String argument) {
        return argumentToPath.apply(argument);
    }
}
