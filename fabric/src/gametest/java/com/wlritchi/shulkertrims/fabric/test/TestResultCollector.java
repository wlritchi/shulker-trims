package com.wlritchi.shulkertrims.fabric.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects test results across multiple test classes to enable running all tests
 * before failing. This allows the Fabric Client GameTest framework to run all
 * entrypoints and produce a complete summary at the end.
 *
 * <p>Usage pattern in test classes:
 * <pre>
 * try {
 *     runSomeTest(context);
 *     TestResultCollector.recordPassed("Test name");
 * } catch (Exception | AssertionError e) {
 *     TestResultCollector.recordFailed("Test name", e);
 * }
 * </pre>
 *
 * <p>Then register {@link TestResultReporter} as the LAST client-gametest entrypoint.
 */
public final class TestResultCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShulkerTrimsTestResults");

    private static int passed = 0;
    private static int failed = 0;
    private static final List<FailedTest> failures = new ArrayList<>();

    private TestResultCollector() {
    }

    /**
     * Records a passed test.
     */
    public static synchronized void recordPassed(String testName) {
        passed++;
        LOGGER.info("PASSED: {}", testName);
    }

    /**
     * Records a failed test with its exception.
     */
    public static synchronized void recordFailed(String testName, Throwable e) {
        failed++;
        failures.add(new FailedTest(testName, e));
        LOGGER.error("FAILED: {}", testName, e);
    }

    /**
     * Returns the total number of passed tests.
     */
    public static synchronized int getPassedCount() {
        return passed;
    }

    /**
     * Returns the total number of failed tests.
     */
    public static synchronized int getFailedCount() {
        return failed;
    }

    /**
     * Returns whether any tests have been recorded yet.
     */
    public static synchronized boolean hasResults() {
        return passed > 0 || failed > 0;
    }

    /**
     * Logs a summary of all test results and throws if any tests failed.
     *
     * @throws AssertionError if any tests failed
     */
    public static synchronized void reportAndFailIfNeeded() {
        LOGGER.info("========================================");
        LOGGER.info("  FINAL TEST SUMMARY");
        LOGGER.info("========================================");
        LOGGER.info("  Passed: {}", passed);
        LOGGER.info("  Failed: {}", failed);
        LOGGER.info("  Total:  {}", passed + failed);
        LOGGER.info("========================================");

        if (failed > 0) {
            LOGGER.error("Failed tests:");
            for (FailedTest failure : failures) {
                LOGGER.error("  - {}: {}", failure.testName, failure.error.getMessage());
            }
            throw new AssertionError(failed + " test(s) failed. See logs above for details.");
        }
    }

    /**
     * Resets all counters. Useful for testing the test framework itself.
     */
    public static synchronized void reset() {
        passed = 0;
        failed = 0;
        failures.clear();
    }

    private record FailedTest(String testName, Throwable error) {
    }
}
