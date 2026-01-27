package com.wlritchi.shulkertrims.fabric.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Final test entrypoint that reports accumulated test results and fails if any tests failed.
 *
 * <p>This MUST be registered as the LAST fabric-client-gametest entrypoint in fabric.mod.json
 * to ensure all other tests have completed before reporting.
 */
@SuppressWarnings("UnstableApiUsage")
public class TestResultReporter implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        // Report all accumulated results and throw if any failed
        TestResultCollector.reportAndFailIfNeeded();
    }
}
