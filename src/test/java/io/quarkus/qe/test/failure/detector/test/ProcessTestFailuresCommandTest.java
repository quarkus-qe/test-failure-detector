package io.quarkus.qe.test.failure.detector.test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusMainTest
public class ProcessTestFailuresCommandTest {

    @Launch(".")
    @Test
    public void testLaunchCommand(LaunchResult result) {
        Assertions.assertTrue(result.getOutput().contains("Looking for test failures"));
    }

    // FIXME: remote following
//    @Test
//    @Launch("World")
//    public void testLaunchCommand(LaunchResult result) {
//        Assertions.assertTrue(result.getOutput().contains("Hello World"));
//    }
//
//    @Test
//    @Launch(value = {}, exitCode = 1)
//    public void testLaunchCommandFailed() {
//    }
//
//    @Test
//    public void testManualLaunch(QuarkusMainLauncher launcher) {
//        LaunchResult result = launcher.launch("Everyone");
//        Assertions.assertEquals(0, result.exitCode());
//        Assertions.assertTrue(result.getOutput().contains("Hello Everyone"));
//    }
}
