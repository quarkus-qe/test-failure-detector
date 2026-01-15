package io.quarkus.qe.test.failure.detector.find.impl;

import io.quarkus.qe.test.failure.detector.find.Failure;
import io.quarkus.qe.test.failure.detector.logger.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Singleton
final class FailSafeFinderStrategy implements FailuresFinderStrategy {

    private static final String FAILSAFE_SUMMARY_XML = "failsafe-summary.xml";
    private static final String TEST_REPORT_PREFIX = "TEST-";

    @Inject
    Logger logger;

    @Override
    public Collection<Failure> find(Path testedProjectDir) {
        List<Failure> failures = new ArrayList<>();

        try {
            List<Path> summaryFiles = findFailsafeSummaries(testedProjectDir);

            for (Path summaryFile : summaryFiles) {

                var summaryFileFailures = processFailsafeSummary(summaryFile);
                if (summaryFileFailures.isEmpty()) {
                    logger.info("Found failsafe summary file " + summaryFile + " with no failures");
                } else {
                    failures.addAll(summaryFileFailures);
                }
            }
        } catch (IOException e) {
            logger.error("Error searching for failsafe reports: " + e.getMessage());
        }

        return failures;
    }

    private List<Path> findFailsafeSummaries(Path rootDir) throws IOException {
        List<Path> summaryFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.getFileName().toString().equals(FAILSAFE_SUMMARY_XML))
                 .forEach(summaryFiles::add);
        }

        return summaryFiles;
    }

    private Collection<Failure> processFailsafeSummary(Path summaryFile) {
        List<Failure> failures = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(summaryFile.toFile());
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            int errorCount = Integer.parseInt(getElementText(root, "errors", "0"));
            int failureCount = Integer.parseInt(getElementText(root, "failures", "0"));

            if (errorCount > 0 || failureCount > 0) {
                logger.info("Found " + errorCount + " errors and " + failureCount + " failures in " + summaryFile);
                // Parse individual test report XMLs in the same directory
                Path reportsDir = summaryFile.getParent();
                failures.addAll(parseTestReports(reportsDir));
            }
        } catch (Exception e) {
            logger.error("Error parsing failsafe summary " + summaryFile + ": " + e.getMessage());
        }

        return failures;
    }

    private Collection<Failure> parseTestReports(Path reportsDir) {
        List<Failure> failures = new ArrayList<>();

        try (Stream<Path> files = Files.list(reportsDir)) {
            files.filter(Files::isRegularFile)
                 .filter(path -> path.getFileName().toString().startsWith(TEST_REPORT_PREFIX))
                 .filter(path -> path.getFileName().toString().endsWith(".xml"))
                 .forEach(reportFile -> failures.addAll(parseTestReport(reportFile)));
        } catch (IOException e) {
            logger.error("Error listing test reports in " + reportsDir + ": " + e.getMessage());
        }

        return failures;
    }

    private Collection<Failure> parseTestReport(Path reportFile) {
        List<Failure> failures = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(reportFile.toFile());
            doc.getDocumentElement().normalize();

            Element testsuite = doc.getDocumentElement();

            // Get all testcase elements
            NodeList testcases = testsuite.getElementsByTagName("testcase");

            for (int i = 0; i < testcases.getLength(); i++) {
                Element testcase = (Element) testcases.item(i);

                // Check for failure element
                NodeList failureNodes = testcase.getElementsByTagName("failure");
                if (failureNodes.getLength() > 0) {
                    Element failureElement = (Element) failureNodes.item(0);
                    failures.add(createFailure(testcase, failureElement, reportFile));
                }

                // Check for error element
                NodeList errorNodes = testcase.getElementsByTagName("error");
                if (errorNodes.getLength() > 0) {
                    Element errorElement = (Element) errorNodes.item(0);
                    failures.add(createFailure(testcase, errorElement, reportFile));
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing test report " + reportFile + ": " + e.getMessage());
        }

        return failures;
    }

    private static Failure createFailure(Element testcase, Element failureElement, Path reportFile) {
        String testClassName = testcase.getAttribute("classname");
        String testMethodName = testcase.getAttribute("name");
        String failureMessage = failureElement.getAttribute("message");
        String failureType = failureElement.getAttribute("type");
        String testRunLog = failureElement.getTextContent();

        // Find the module path by looking for pom.xml in parent directories
        String modulePath = findModulePath(reportFile);

        // Determine if this is a failure or error based on the element tag name
        Failure.FailureType type = failureElement.getTagName().equals("error")
                ? Failure.FailureType.ERROR
                : Failure.FailureType.FAILURE;

        return new FailureRecord(testClassName, testMethodName, modulePath, failureMessage, type, failureType, testRunLog);
    }

    private static String findModulePath(Path reportFile) {
        Path current = reportFile.getParent();

        // Navigate up to find the directory containing pom.xml
        while (current != null) {
            Path pomFile = current.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                return current.toAbsolutePath().toString();
            }
            current = current.getParent();
        }

        return reportFile.getParent().toAbsolutePath().toString();
    }

    private static String getElementText(Element parent, String tagName, String defaultValue) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return defaultValue;
    }

    private record FailureRecord(
            String testClassName,
            String testMethodName,
            String modulePath,
            String failureMessage,
            Failure.FailureType failureType,
            String throwableClass,
            String testRunLog) implements Failure {

        @Override
        public String toString() {
            return "FailureRecord[" +
                    "testClassName=" + testClassName +
                    ", testMethodName=" + testMethodName +
                    ", modulePath=" + modulePath +
                    ", failureType=" + failureType +
                    ", throwableClass=" + throwableClass +
                    ']';
        }
    }
}
