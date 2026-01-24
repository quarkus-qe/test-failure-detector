package io.quarkus.qe.test.failure.detector.analyze.impl;

import io.quarkus.qe.test.failure.detector.analyze.AnalysisMetadata;
import io.quarkus.qe.test.failure.detector.analyze.FailureDetails;
import io.quarkus.qe.test.failure.detector.analyze.FailuresAnalyzer;
import io.quarkus.qe.test.failure.detector.analyze.RootCause;
import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import jakarta.enterprise.context.Dependent;

import java.util.HashMap;
import java.util.Map;

@Dependent // this bean is stateful, so keep it in "command execution scope"
final class FailuresAnalyzerImpl implements FailuresAnalyzer {

    private final Logger logger;

    private final Map<String, AnalyzedRootCause> rootCausesByClass;

    private final Map<String, AnalyzedRootCause> rootCausesByModule;

    FailuresAnalyzerImpl(Logger logger) {
        this.logger = logger;
        this.rootCausesByClass = new HashMap<>();
        this.rootCausesByModule = new HashMap<>();
    }

    @Override
    public RootCause analyze(Failure failure) {
        logger.info("Analyzing test failure: " + failure);

        if (rootCausesByClass.containsKey(failure.testClassName())) {
            return addToExistingRootCause(rootCausesByClass, failure.testClassName(), failure);
        }

        if (rootCausesByModule.containsKey(failure.modulePath())) {
            return addToExistingRootCause(rootCausesByModule, failure.modulePath(), failure);
        }

        return createNewRootCause(failure);
    }

    private AnalyzedRootCause addToExistingRootCause(
            Map<String, AnalyzedRootCause> trackingMap,
            String key,
            Failure failure) {

        AnalyzedRootCause existing = trackingMap.get(key);
        FailureDetails newFailureDetails = FailureDetails.from(failure, false);
        AnalyzedRootCause updated = existing.addFailure(newFailureDetails);

        // Update tracking maps
        trackingMap.put(key, updated);
        if (trackingMap == rootCausesByClass) {
            // Also update module map to keep them in sync
            rootCausesByModule.put(failure.modulePath(), updated);
        } else {
            // Module map was updated, sync class map if the primary failure class matches
            String primaryFailureClass = existing.failures().get(0).testClassName();
            rootCausesByClass.put(primaryFailureClass, updated);
        }

        logger.info("Added failure to existing root cause: " + updated.identifier() +
                " (now " + updated.failures().size() + " failures)");

        return updated;
    }

    private AnalyzedRootCause createNewRootCause(Failure failure) {
        // First occurrence = HIGH confidence (we're confident this is the primary failure)
        AnalyzedRootCause.ConfidenceLevel confidence = AnalyzedRootCause.ConfidenceLevel.HIGH;
        AnalysisMetadata.DeduplicationStrategy strategy = AnalysisMetadata.DeduplicationStrategy.BY_CLASS;

        String identifier = createIdentifier(failure, strategy);
        String summary = createSummary(failure);
        FailureDetails primaryFailure = FailureDetails.from(failure, true);
        AnalysisMetadata metadata = AnalysisMetadata.create(strategy);

        // FIXME: find the root cause!!!!!!!!!!!!!!
        AnalyzedRootCause rootCause = AnalyzedRootCause.create(
                identifier,
                failure.modulePath(),
                summary,
                confidence,
                primaryFailure,
                metadata
        );

        rootCausesByClass.put(failure.testClassName(), rootCause);
        rootCausesByModule.put(failure.modulePath(), rootCause);

        logger.info("Created new root cause: " + identifier);

        return rootCause;
    }

    /**
     * Create a unique identifier for a root cause.
     */
    private static String createIdentifier(Failure failure, AnalysisMetadata.DeduplicationStrategy strategy) {
        return switch (strategy) {
            case BY_CLASS -> "CLASS:" + failure.testClassName();
            case BY_MODULE -> "MODULE:" + failure.modulePath();
            case NONE -> "FAILURE:" + failure.testClassName() + "#" + failure.testMethodName();
        };
    }

    /**
     * Create a human-readable summary of the failure.
     */
    private static String createSummary(Failure failure) {
        String className = getSimpleClassName(failure.testClassName());
        String exceptionType = getSimpleClassName(failure.throwableClass());

        return "Test failure in " + className + " - " + exceptionType;
    }

    /**
     * Extract simple class name from fully qualified name.
     */
    private static String getSimpleClassName(String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            return "Unknown";
        }
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }
}
