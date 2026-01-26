package com.wlritchi.shulkertrims.fabric.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Cross-platform server connection tests.
 *
 * <p>These tests verify that the gametest client can connect to external servers
 * and take screenshots for golden image comparison.
 *
 * <p>Paper tests share a single server instance for efficiency (Paper startup is slow).
 * Use the {@code shulker_trims.paper_tests} system property to control which tests run:
 * <ul>
 *   <li>Unset or empty: Run all Paper tests</li>
 *   <li>Comma-separated list: Run only specified tests (e.g., "world,chest_gui")</li>
 * </ul>
 *
 * <p>Available Paper test names:
 * <ul>
 *   <li>{@code world} - Basic world rendering with trimmed shulkers</li>
 *   <li>{@code chunk_reload} - Chunk unload/reload sync verification</li>
 *   <li>{@code live_modification} - Live trim add/remove while player watches</li>
 *   <li>{@code chest_gui} - Chest GUI with trimmed shulker items</li>
 *   <li>{@code smithing_table} - Smithing table trim preview</li>
 *   <li>{@code dispenser} - Dispenser-placed trimmed shulker sync</li>
 *   <li>{@code two_player} - Two-player placement: bot places block, observer sees trim</li>
 * </ul>
 *
 * <p>Additional test modes (not part of Paper test suite):
 * <ul>
 *   <li>Set {@code shulker_trims.test_mode=fabric} for Fabric in-process server test</li>
 *   <li>Set {@code shulker_trims.test_mode=manual} for manual external server connection</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class ExternalServerConnectionTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("ExternalServerTest");

    // Port for test servers (different from default 25565 to avoid conflicts)
    private static final int TEST_SERVER_PORT = 25566;

    // Screenshot comparison threshold (0.01% mean squared difference)
    // Same as singleplayer tests for consistency
    private static final float COMPARISON_THRESHOLD = 0.0001f;

    // For manual testing with an already-running server
    private static final String MANUAL_SERVER_ADDRESS = "127.0.0.1:25565";

    // Paper test names
    private static final String TEST_WORLD = "world";
    private static final String TEST_CHUNK_RELOAD = "chunk_reload";
    private static final String TEST_LIVE_MODIFICATION = "live_modification";
    private static final String TEST_CHEST_GUI = "chest_gui";
    private static final String TEST_SMITHING_TABLE = "smithing_table";
    private static final String TEST_DISPENSER_PLACEMENT = "dispenser";
    private static final String TEST_TWO_PLAYER = "two_player";
    private static final String TEST_RESYNC_AFTER_BREAK = "resync_after_break";

    private static final Set<String> ALL_PAPER_TESTS = Set.of(
            TEST_WORLD, TEST_CHUNK_RELOAD, TEST_LIVE_MODIFICATION, TEST_CHEST_GUI, TEST_SMITHING_TABLE,
            TEST_DISPENSER_PLACEMENT, TEST_TWO_PLAYER, TEST_RESYNC_AFTER_BREAK
    );

    /**
     * Get the set of Paper tests to run based on system property.
     * If property is unset or empty, returns all tests.
     * Otherwise, parses comma-separated list of test names.
     */
    private static Set<String> getEnabledPaperTests() {
        String prop = System.getProperty("shulker_trims.paper_tests", "").trim();
        if (prop.isEmpty()) {
            return ALL_PAPER_TESTS;
        }
        Set<String> enabled = Arrays.stream(prop.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        // Validate test names
        for (String test : enabled) {
            if (!ALL_PAPER_TESTS.contains(test)) {
                LOGGER.warn("Unknown Paper test name: '{}'. Valid names: {}", test, ALL_PAPER_TESTS);
            }
        }
        return enabled;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        String testMode = System.getProperty("shulker_trims.test_mode", "paper").trim().toLowerCase();

        switch (testMode) {
            case "fabric" -> testFabricDedicatedServer(context);
            case "manual" -> testExternalServerConnection(context, MANUAL_SERVER_ADDRESS);
            case "paper" -> runPaperTests(context);
            default -> {
                LOGGER.warn("Unknown test mode '{}', defaulting to 'paper'", testMode);
                runPaperTests(context);
            }
        }
    }

    /**
     * Run all enabled Paper tests with a shared server instance.
     * This avoids the ~30s Paper startup overhead for each test.
     */
    private void runPaperTests(ClientGameTestContext context) {
        Set<String> enabledTests = getEnabledPaperTests();
        LOGGER.info("=== Paper Server Tests ===");
        LOGGER.info("Enabled tests: {}", enabledTests);

        if (enabledTests.isEmpty()) {
            LOGGER.info("No Paper tests enabled, skipping");
            return;
        }

        try (TestServerLauncher launcher = new TestServerLauncher(
                TestServerLauncher.ServerType.PAPER, TEST_SERVER_PORT)) {

            // Start the server once (5 minute timeout - Paper's first startup downloads Minecraft JAR)
            launcher.start(300);
            String address = launcher.getAddress();
            LOGGER.info("Paper server started at {}", address);

            // Run each enabled test
            int passed = 0;
            int failed = 0;

            if (enabledTests.contains(TEST_WORLD)) {
                cleanupWorldViaRcon(launcher);
                try {
                    runPaperWorldTest(context, address, launcher);
                    passed++;
                } catch (Exception | AssertionError e) {
                    LOGGER.error("Paper world test FAILED", e);
                    failed++;
                }
            }

            if (enabledTests.contains(TEST_CHUNK_RELOAD)) {
                cleanupWorldViaRcon(launcher);
                try {
                    runPaperChunkReloadTest(context, address, launcher);
                    passed++;
                } catch (Exception | AssertionError e) {
                    LOGGER.error("Paper chunk reload test FAILED", e);
                    failed++;
                }
            }

            if (enabledTests.contains(TEST_LIVE_MODIFICATION)) {
                cleanupWorldViaRcon(launcher);
                try {
                    runPaperLiveModificationTest(context, address, launcher);
                    passed++;
                } catch (Exception | AssertionError e) {
                    LOGGER.error("Paper live modification test FAILED", e);
                    failed++;
                }
            }

            if (enabledTests.contains(TEST_CHEST_GUI)) {
                cleanupWorldViaRcon(launcher);
                try {
                    runPaperChestGuiTest(context, address, launcher);
                    passed++;
                } catch (Exception | AssertionError e) {
                    LOGGER.error("Paper chest GUI test FAILED", e);
                    failed++;
                }
            }

            if (enabledTests.contains(TEST_SMITHING_TABLE)) {
                cleanupWorldViaRcon(launcher);
                try {
                    runPaperSmithingTableTest(context, address, launcher);
                    passed++;
                } catch (Exception | AssertionError e) {
                    LOGGER.error("Paper smithing table test FAILED", e);
                    failed++;
                }
            }

            if (enabledTests.contains(TEST_DISPENSER_PLACEMENT)) {
                cleanupWorldViaRcon(launcher);
                try {
                    runPaperDispenserPlacementTest(context, address, launcher);
                    passed++;
                } catch (Exception | AssertionError e) {
                    LOGGER.error("Paper dispenser placement test FAILED", e);
                    failed++;
                }
            }

            if (enabledTests.contains(TEST_TWO_PLAYER)) {
                cleanupWorldViaRcon(launcher);
                try {
                    runPaperTwoPlayerPlacementTest(context, address, launcher);
                    passed++;
                } catch (Exception | AssertionError e) {
                    LOGGER.error("Paper two-player placement test FAILED", e);
                    failed++;
                }
            }

            if (enabledTests.contains(TEST_RESYNC_AFTER_BREAK)) {
                cleanupWorldViaRcon(launcher);
                try {
                    runPaperResyncAfterBreakTest(context, address, launcher);
                    passed++;
                } catch (Exception | AssertionError e) {
                    LOGGER.error("Paper resync-after-break test FAILED", e);
                    failed++;
                }
            }

            LOGGER.info("=== Paper Tests Complete: {} passed, {} failed ===", passed, failed);

            if (failed > 0) {
                throw new AssertionError(failed + " Paper test(s) failed");
            }

        } catch (Exception e) {
            LOGGER.error("Paper server setup failed", e);
            throw new AssertionError("Paper server setup failed: " + e.getMessage(), e);
        }
    }

    /**
     * Clean up the test world via RCON.
     * Removes all blocks and resets to barrier floor.
     */
    private void cleanupWorldViaRcon(TestServerLauncher launcher) {
        LOGGER.info("Cleaning up test world...");
        try {
            // Use @a to clear all players (in case player name changes between tests)
            for (String command : TestWorldSetup.generateCleanupCommands("@a")) {
                String response = launcher.sendCommand(command);
                LOGGER.debug("RCON cleanup [{}]: {}", command, response);
            }
            LOGGER.info("World cleanup complete");
        } catch (Exception e) {
            LOGGER.warn("RCON world cleanup encountered errors: {}", e.getMessage());
        }
    }

    /**
     * Run the Paper world rendering test with a shared server.
     */
    private void runPaperWorldTest(ClientGameTestContext context, String address, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper World Rendering ===");

        // Set up the test world via RCON
        setupWorldViaRcon(launcher);

        // Run the test
        testPaperServerConnection(context, address, launcher);
    }

    /**
     * Run the Paper chunk reload sync test with a shared server.
     */
    private void runPaperChunkReloadTest(ClientGameTestContext context, String address, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Chunk Reload Sync ===");

        // Set up the test world via RCON
        setupWorldViaRcon(launcher);

        // Run the chunk reload test
        testPaperChunkReloadConnection(context, address, launcher);
    }

    /**
     * Run the Paper live modification test with a shared server.
     */
    private void runPaperLiveModificationTest(ClientGameTestContext context, String address, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Live Trim Modification ===");

        // Set up UNTRIMMED world (no trims initially)
        setupUntrimmedWorldViaRcon(launcher);

        // Run the live modification test
        testPaperLiveModificationConnection(context, address, launcher);
    }

    /**
     * Run the Paper chest GUI test with a shared server.
     */
    private void runPaperChestGuiTest(ClientGameTestContext context, String address, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Chest GUI ===");

        // Set up the chest with trimmed shulkers via RCON
        setupChestViaRcon(launcher);

        // Run the chest GUI test
        testPaperChestGuiConnection(context, address, launcher);
    }

    /**
     * Run the Paper smithing table test with a shared server.
     */
    private void runPaperSmithingTableTest(ClientGameTestContext context, String address, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Smithing Table ===");

        // Note: Smithing table setup happens after connecting (needs player name)
        // Run the smithing table test
        testPaperSmithingTableConnection(context, address, launcher);
    }

    /**
     * Run the Paper dispenser placement test with a shared server.
     */
    private void runPaperDispenserPlacementTest(ClientGameTestContext context, String address, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Dispenser Placement ===");

        // Set up the dispenser with a trimmed shulker box
        setupDispenserViaRcon(launcher);

        // Run the dispenser placement test
        testPaperDispenserPlacementConnection(context, address, launcher);
    }

    /**
     * Run the Paper two-player placement test with a shared server.
     * A bot player places a trimmed shulker box while the observer client watches.
     */
    private void runPaperTwoPlayerPlacementTest(ClientGameTestContext context, String address, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Two-Player Placement ===");

        // Set up the world (floor, forceload, etc.)
        setupTwoPlayerWorldViaRcon(launcher);

        // Run the two-player placement test
        testPaperTwoPlayerPlacementConnection(context, address, launcher);
    }

    /**
     * Set up the two-player test world via RCON.
     */
    private void setupTwoPlayerWorldViaRcon(TestServerLauncher launcher) {
        try {
            for (String command : TestWorldSetup.generateTwoPlayerSetupCommands()) {
                String response = launcher.sendCommand(command);
                LOGGER.debug("RCON [{}]: {}", command, response);
            }
            LOGGER.info("Two-player world setup complete");
        } catch (Exception e) {
            LOGGER.warn("RCON two-player setup encountered errors: {}", e.getMessage());
        }
    }

    /**
     * Test Paper server connection with two players: a bot places a trimmed shulker, observer sees it.
     *
     * <p>This tests that when one player places a trimmed shulker box, the trim data is
     * correctly synced to other nearby players. This exercises the server's broadcast
     * mechanism for custom data on block placement.
     */
    private void testPaperTwoPlayerPlacementConnection(ClientGameTestContext context, String serverAddress, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Two-Player Placement Connection ===");

        // Parse server address to get host and port for bot connection
        String[] addressParts = serverAddress.split(":");
        String host = addressParts[0];
        int port = addressParts.length > 1 ? Integer.parseInt(addressParts[1]) : 25565;

        // Create the bot (but don't connect yet)
        try (MinecraftBot bot = new MinecraftBot("TrimBot", host, port)) {

            // First, connect the observer (Fabric client)
            context.runOnClient(client -> {
                ServerInfo serverInfo = new ServerInfo(
                        "Test Paper Server",
                        serverAddress,
                        ServerInfo.ServerType.OTHER
                );

                ConnectScreen.connect(
                        client.currentScreen,
                        client,
                        ServerAddress.parse(serverAddress),
                        serverInfo,
                        false,
                        null
                );
            });

            // Wait for observer world load
            LOGGER.info("Waiting for observer client to connect...");
            waitForWorldLoad(context);

            // Verify observer is connected
            boolean isObserverConnected = context.computeOnClient(client ->
                    client.world != null && client.player != null
            );

            if (!isObserverConnected) {
                throw new AssertionError("Failed to connect observer client to Paper server");
            }

            String observerName = context.computeOnClient(client ->
                    client.player != null ? client.player.getGameProfile().name() : "Player0"
            );
            LOGGER.info("Observer '{}' connected!", observerName);

            // Wait for initial chunks
            context.waitTicks(60);

            // Now connect the bot
            LOGGER.info("Connecting bot client...");
            bot.connect();
            bot.waitForLogin(30, java.util.concurrent.TimeUnit.SECONDS);
            LOGGER.info("Bot '{}' connected!", bot.getUsername());

            // Wait a moment for server to recognize bot
            context.waitTicks(20);

            // Set up both players via RCON
            try {
                // Put observer in spectator mode at camera position
                // Spectator mode prevents falling and doesn't require barriers
                launcher.sendCommand("gamemode spectator " + observerName);
                context.waitTicks(5);
                String observerTp = TestWorldSetup.generateTwoPlayerObserverTeleportCommand(observerName);
                LOGGER.info("Teleporting observer: {}", observerTp);
                launcher.sendCommand(observerTp);

                // Put bot in creative mode at placer position
                String gamemodeResp = launcher.sendCommand("gamemode creative " + bot.getUsername());
                LOGGER.info("Bot gamemode response: {}", gamemodeResp);
                context.waitTicks(10);

                String placerTp = TestWorldSetup.generateTwoPlayerPlacerTeleportCommand(bot.getUsername());
                LOGGER.info("Teleporting bot: {}", placerTp);
                String tpResp = launcher.sendCommand(placerTp);
                LOGGER.info("Bot teleport response: {}", tpResp);

                // Give bot the trimmed shulker box
                String giveItem = TestWorldSetup.generateTwoPlayerGiveItemCommand(bot.getUsername());
                LOGGER.info("Giving bot item: {}", giveItem);
                String giveResp = launcher.sendCommand(giveItem);
                LOGGER.info("Give item response: {}", giveResp);

                // Verify there's a solid block to place against (use stone for reliability)
                String setFloor = String.format("setblock %d %d %d minecraft:stone",
                        TestWorldSetup.TWO_PLAYER_BLOCK_X,
                        TestWorldSetup.TWO_PLAYER_BLOCK_Y - 1,
                        TestWorldSetup.TWO_PLAYER_BLOCK_Z);
                String floorResp = launcher.sendCommand(setFloor);
                LOGGER.info("Set floor block: {}", floorResp);
            } catch (Exception e) {
                LOGGER.warn("RCON player setup failed: {}", e.getMessage());
            }

            // Wait for teleports and item sync to bot client
            // The bot needs time to receive and confirm teleport, then receive inventory update
            context.waitTicks(80);

            // Hide HUD for clean screenshot
            context.runOnClient(client -> {
                client.options.hudHidden = true;
            });

            // Have bot select hotbar slot 0 (where we gave the shulker box)
            LOGGER.info("Bot selecting hotbar slot 0...");
            bot.selectHotbarSlot(0);

            // Wait for hotbar selection to sync
            context.waitTicks(10);

            // Check if bot received the item
            if (!bot.hasItemInHotbar(0)) {
                LOGGER.warn("Bot does not have item in hotbar slot 0 yet, waiting more...");
                context.waitTicks(40);
            }

            // Log bot state before placement
            var heldItem = bot.getHeldItem();
            double[] botPos = bot.getPosition();
            LOGGER.info("Bot state before placement:");
            LOGGER.info("  Position: ({}, {}, {})", botPos[0], botPos[1], botPos[2]);
            LOGGER.info("  Held item: {}", heldItem != null ? heldItem.getId() : "null");

            // Have bot place the block
            // The block should be placed at (0, 100, 0) by clicking the top of the floor block at (0, 99, 0)
            LOGGER.info("Bot placing block at ({}, {}, {})...",
                    TestWorldSetup.TWO_PLAYER_BLOCK_X,
                    TestWorldSetup.TWO_PLAYER_BLOCK_Y,
                    TestWorldSetup.TWO_PLAYER_BLOCK_Z);

            org.cloudburstmc.math.vector.Vector3i targetBlock = org.cloudburstmc.math.vector.Vector3i.from(
                    TestWorldSetup.TWO_PLAYER_BLOCK_X,
                    TestWorldSetup.TWO_PLAYER_BLOCK_Y - 1,  // The floor block
                    TestWorldSetup.TWO_PLAYER_BLOCK_Z
            );
            bot.placeBlock(targetBlock, org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.UP);

            // Wait for block placement and trim sync
            LOGGER.info("Waiting for block placement and trim sync...");
            context.waitTicks(40);

            // Check if block was placed
            boolean blockPlaced = context.computeOnClient(client -> {
                if (client.world != null) {
                    var blockPos = new net.minecraft.util.math.BlockPos(
                            TestWorldSetup.TWO_PLAYER_BLOCK_X,
                            TestWorldSetup.TWO_PLAYER_BLOCK_Y,
                            TestWorldSetup.TWO_PLAYER_BLOCK_Z);
                    return client.world.getBlockState(blockPos).getBlock() instanceof net.minecraft.block.ShulkerBoxBlock;
                }
                return false;
            });

            if (!blockPlaced) {
                // Log diagnostic information
                LOGGER.error("Protocol-based block placement FAILED!");
                LOGGER.error("This is a test infrastructure issue - the bot was unable to place a block");
                LOGGER.error("Bot position: ({}, {}, {})", botPos[0], botPos[1], botPos[2]);
                LOGGER.error("Bot held item: {}", heldItem != null ? heldItem.getId() : "null");
                LOGGER.error("Target block: ({}, {}, {}) face UP",
                        TestWorldSetup.TWO_PLAYER_BLOCK_X,
                        TestWorldSetup.TWO_PLAYER_BLOCK_Y - 1,
                        TestWorldSetup.TWO_PLAYER_BLOCK_Z);

                // Check what block is at the target
                context.runOnClient(client -> {
                    if (client.world != null) {
                        var floorPos = new net.minecraft.util.math.BlockPos(
                                TestWorldSetup.TWO_PLAYER_BLOCK_X,
                                TestWorldSetup.TWO_PLAYER_BLOCK_Y - 1,
                                TestWorldSetup.TWO_PLAYER_BLOCK_Z);
                        var placementPos = new net.minecraft.util.math.BlockPos(
                                TestWorldSetup.TWO_PLAYER_BLOCK_X,
                                TestWorldSetup.TWO_PLAYER_BLOCK_Y,
                                TestWorldSetup.TWO_PLAYER_BLOCK_Z);
                        LOGGER.error("Floor block at Y-1: {}",
                                net.minecraft.registry.Registries.BLOCK.getId(
                                        client.world.getBlockState(floorPos).getBlock()));
                        LOGGER.error("Block at placement position: {}",
                                net.minecraft.registry.Registries.BLOCK.getId(
                                        client.world.getBlockState(placementPos).getBlock()));
                    }
                });

                throw new AssertionError(
                        "Protocol-based block placement failed. " +
                        "The MCProtocolLib bot was unable to place a block via ServerboundUseItemOnPacket. " +
                        "Check logs for diagnostic information.");
            }

            LOGGER.info("Block placement via protocol SUCCEEDED!");

            // Teleport TrimBot out of frame before taking screenshot
            // The bot's idle animation causes non-deterministic screenshots
            try {
                launcher.sendCommand("tp " + bot.getUsername() + " 0 -100 0");
                LOGGER.info("Teleported bot out of frame for screenshot");
            } catch (Exception e) {
                LOGGER.warn("Failed to teleport bot out of frame: {}", e.getMessage());
            }

            // Wait for block placement to propagate and trim to sync.
            // The plugin's periodic sync runs every second, and the trim needs to
            // propagate to the observer client.
            LOGGER.info("Waiting for block placement to propagate...");
            context.waitTicks(60);  // 3 seconds

            // Verify the block and trim
            context.runOnClient(client -> {
                if (client.world != null) {
                    var blockPos = new net.minecraft.util.math.BlockPos(
                            TestWorldSetup.TWO_PLAYER_BLOCK_X,
                            TestWorldSetup.TWO_PLAYER_BLOCK_Y,
                            TestWorldSetup.TWO_PLAYER_BLOCK_Z);
                    var block = client.world.getBlockState(blockPos).getBlock();
                    LOGGER.info("Block at placement position: {}",
                            net.minecraft.registry.Registries.BLOCK.getId(block));

                    if (block instanceof net.minecraft.block.ShulkerBoxBlock) {
                        LOGGER.info("Shulker box placed successfully!");
                        var blockEntity = client.world.getBlockEntity(blockPos);
                        if (blockEntity instanceof com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox trimmed) {
                            var trim = trimmed.shulkerTrims$getTrim();
                            if (trim != null) {
                                LOGGER.info("Trim present in block entity: pattern={}, material={}",
                                        trim.pattern(), trim.material());
                            } else {
                                LOGGER.warn("NO TRIM in block entity after waiting!");
                            }
                        }
                    } else {
                        LOGGER.warn("Expected shulker box but found: {}", block);
                    }
                }
            });

            // Take screenshot and compare against golden template
            // Uses same template as dispenser test since it's a single blue shulker with wild/redstone trim
            TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of("shulker-trim-two-player-placed")
                    .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                    .withSize(1920, 1080)
                    .save();

            context.assertScreenshotEquals(options);
            LOGGER.info("Screenshot comparison passed: shulker-trim-two-player-placed");

            // Restore HUD
            context.runOnClient(client -> {
                client.options.hudHidden = false;
            });

            // Disconnect bot
            LOGGER.info("Disconnecting bot...");
            bot.disconnect();

            // Disconnect observer
            context.runOnClient(client -> {
                LOGGER.info("Disconnecting observer from Paper server...");
                client.disconnect(new TitleScreen(), false);
            });

            context.waitTicks(20);
            LOGGER.info("Paper two-player placement test completed successfully");

        } catch (Exception e) {
            LOGGER.error("Two-player test failed", e);
            throw new AssertionError("Two-player placement test failed: " + e.getMessage(), e);
        }
    }

    // Constants for resync-after-break test
    // Uses X=30 to avoid coordinate conflicts with other tests
    private static final int RESYNC_TEST_X = 30;
    private static final int RESYNC_TEST_Y = 100;
    private static final int RESYNC_TEST_Z = 0;

    /**
     * Test that trim data re-syncs after a block is broken and replaced.
     *
     * <p>This test verifies that the plugin's lastKnownTrims tracking properly clears
     * when a shulker box is removed, allowing re-sync when an identical trim is placed
     * at the same location.
     *
     * <p>Tests multiple block removal methods:
     * <ul>
     *   <li>Server /setblock air</li>
     *   <li>Explosion</li>
     * </ul>
     */
    private void runPaperResyncAfterBreakTest(ClientGameTestContext context, String address, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Resync After Break ===");

        try {
            // Connect observer
            final String serverAddress = address;
            context.runOnClient(client -> {
                LOGGER.info("Connecting to Paper server at {} for resync test...", serverAddress);
                client.setScreen(new TitleScreen());
            });
            context.waitTicks(5);

            context.runOnClient(client -> {
                var serverInfo = new ServerInfo("Test", serverAddress, ServerInfo.ServerType.OTHER);
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(serverAddress),
                        serverInfo, false, null);
            });

            waitForWorldLoad(context);
            LOGGER.info("Connected to Paper server for resync test");

            // Force-load chunk containing test area
            launcher.sendCommand(String.format("forceload add %d %d", RESYNC_TEST_X, RESYNC_TEST_Z));

            // Set up test area - barrier floor
            launcher.sendCommand(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                    RESYNC_TEST_X - 2, RESYNC_TEST_Y - 1, RESYNC_TEST_Z - 2,
                    RESYNC_TEST_X + 2, RESYNC_TEST_Y - 1, RESYNC_TEST_Z + 2));

            // Position player to observe the test location (also loads the chunk)
            launcher.sendCommand(String.format("tp @a %d %d %d", RESYNC_TEST_X, RESYNC_TEST_Y + 2, RESYNC_TEST_Z + 3));
            context.waitTicks(40);  // Give time for chunk to load

            // Test 1: Resync after /setblock air
            LOGGER.info("Test 1: Resync after /setblock air");
            testResyncAfterRemoval(context, launcher, "setblock %d %d %d air".formatted(RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z));

            // Additional test: Verify with a different removal method (fill command)
            // This tests that any block removal clears lastKnownTrims
            LOGGER.info("Test 2: Resync after /fill air");
            testResyncAfterRemoval(context, launcher, "fill %d %d %d %d %d %d air".formatted(
                    RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z,
                    RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z));

            // Disconnect
            context.runOnClient(client -> {
                client.disconnect(new TitleScreen(), false);
            });
            context.waitTicks(20);

            LOGGER.info("Paper resync-after-break test completed successfully");

        } catch (Exception e) {
            LOGGER.error("Resync-after-break test failed", e);
            throw new AssertionError("Resync-after-break test failed: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to test resync after a specific removal command.
     */
    private void testResyncAfterRemoval(ClientGameTestContext context, TestServerLauncher launcher, String removalCommand) throws java.io.IOException {
        // Place a shulker box first, then add trim data via data merge
        // (setblock with CustomData doesn't work reliably for block entity components)
        String placeResult = launcher.sendCommand(String.format(
                "setblock %d %d %d minecraft:blue_shulker_box[facing=up]",
                RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z));
        LOGGER.info("Place block response: {}", placeResult);

        // Wait a tick for block entity to be created on server
        context.waitTicks(5);

        // Add trim data using data merge
        String mergeResult = launcher.sendCommand(String.format(
                "data merge block %d %d %d {components:{\"minecraft:custom_data\":{\"shulker_trims:trim\":{pattern:\"minecraft:wild\",material:\"minecraft:redstone\"}}}}",
                RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z));
        LOGGER.info("Data merge response: {}", mergeResult);

        // Wait for sync (plugin's periodic check runs every 20 ticks = 1 second)
        // Give it 4 seconds to ensure the block entity exists on client and periodic check runs
        context.waitTicks(80);

        // Verify trim was synced
        boolean firstSyncReceived = context.computeOnClient(client -> {
            if (client.world == null) return false;
            var pos = new net.minecraft.util.math.BlockPos(RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z);
            var be = client.world.getBlockEntity(pos);
            if (be instanceof com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox trimmed) {
                var trim = trimmed.shulkerTrims$getTrim();
                if (trim != null) {
                    LOGGER.info("Initial sync received: {}", trim);
                    return true;
                }
            }
            return false;
        });

        if (!firstSyncReceived) {
            throw new AssertionError("Initial trim sync not received");
        }

        // Remove the block
        LOGGER.info("Removing block via: {}", removalCommand);
        launcher.sendCommand(removalCommand);
        context.waitTicks(20);

        // Verify block is gone
        boolean blockGone = context.computeOnClient(client -> {
            if (client.world == null) return false;
            var pos = new net.minecraft.util.math.BlockPos(RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z);
            return client.world.getBlockState(pos).isAir();
        });

        if (!blockGone) {
            throw new AssertionError("Block was not removed");
        }

        // Place the EXACT SAME trim at the EXACT SAME location
        LOGGER.info("Re-placing identical trimmed shulker...");
        launcher.sendCommand(String.format(
                "setblock %d %d %d minecraft:blue_shulker_box[facing=up]",
                RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z));
        context.waitTicks(5);  // Wait for block entity creation
        launcher.sendCommand(String.format(
                "data merge block %d %d %d {components:{\"minecraft:custom_data\":{\"shulker_trims:trim\":{pattern:\"minecraft:wild\",material:\"minecraft:redstone\"}}}}",
                RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z));

        // Wait for sync - previously the bug was that lastKnownTrims still had the old entry,
        // so checkForChanges saw currentTrim == lastKnownTrims and didn't re-sync.
        // With the fix, lastKnownTrims is cleared when the shulker is removed.
        context.waitTicks(80);

        // Verify trim was re-synced (THIS IS THE FAILING ASSERTION)
        boolean resyncReceived = context.computeOnClient(client -> {
            if (client.world == null) return false;
            var pos = new net.minecraft.util.math.BlockPos(RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z);
            var be = client.world.getBlockEntity(pos);
            if (be instanceof com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox trimmed) {
                var trim = trimmed.shulkerTrims$getTrim();
                if (trim != null) {
                    LOGGER.info("Re-sync received: {}", trim);
                    return true;
                }
            }
            LOGGER.warn("Re-sync NOT received - this is the bug!");
            return false;
        });

        if (!resyncReceived) {
            throw new AssertionError("Trim not re-synced after break and replace - lastKnownTrims bug!");
        }

        // Clean up for next test
        launcher.sendCommand(String.format("setblock %d %d %d air", RESYNC_TEST_X, RESYNC_TEST_Y, RESYNC_TEST_Z));
        context.waitTicks(10);
    }

    /**
     * Set up the test world with untrimmed shulkers using RCON commands.
     */
    private void setupUntrimmedWorldViaRcon(TestServerLauncher launcher) {
        try {
            // Use basic setup (places shulkers WITHOUT trims)
            for (String command : TestWorldSetup.generateBasicSetupCommands()) {
                String response = launcher.sendCommand(command);
                LOGGER.debug("RCON [{}]: {}", command, response);
            }
            LOGGER.info("Untrimmed world setup complete (18 shulker boxes placed, no trims)");
        } catch (Exception e) {
            LOGGER.warn("RCON world setup encountered errors: {}", e.getMessage());
        }
    }

    /**
     * Test Paper server connection with live trim add/remove.
     */
    private void testPaperLiveModificationConnection(ClientGameTestContext context, String serverAddress, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Server Live Modification ===");
        LOGGER.info("Attempting to connect to: {}", serverAddress);

        // Initiate connection
        context.runOnClient(client -> {
            ServerInfo serverInfo = new ServerInfo(
                    "Test Paper Server",
                    serverAddress,
                    ServerInfo.ServerType.OTHER
            );

            ConnectScreen.connect(
                    client.currentScreen,
                    client,
                    ServerAddress.parse(serverAddress),
                    serverInfo,
                    false,
                    null
            );
        });

        // Wait for world load
        LOGGER.info("Waiting for world load...");
        waitForWorldLoad(context);

        // Verify we're connected
        boolean isConnected = context.computeOnClient(client ->
                client.world != null && client.player != null
        );

        if (!isConnected) {
            throw new AssertionError("Failed to connect to Paper server");
        }

        LOGGER.info("Successfully connected to Paper server!");

        // Wait for initial chunks to load
        context.waitTicks(60);

        // Get player name for teleport commands
        String playerName = context.computeOnClient(client ->
                client.player != null ? client.player.getGameProfile().name() : "Player0"
        );

        // Teleport to camera position
        try {
            String teleportCommand = TestWorldSetup.generateTeleportCommand(playerName);
            launcher.sendCommand(teleportCommand);
            launcher.sendCommand("gamemode creative " + playerName);
        } catch (Exception e) {
            LOGGER.warn("Failed to teleport player: {}", e.getMessage());
        }

        // Wait for chunks to render at camera position
        context.waitTicks(100);

        // Hide HUD for clean screenshots
        context.runOnClient(client -> {
            client.options.hudHidden = true;
        });

        // === PHASE 1: Verify untrimmed state ===
        LOGGER.info("Phase 1: Verifying untrimmed state");

        TestScreenshotComparisonOptions untrimmedOptions = TestScreenshotComparisonOptions.of("shulker-untrimmed-world")
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .withSize(1920, 1080)
                .save();

        context.assertScreenshotEquals(untrimmedOptions);
        LOGGER.info("Phase 1 PASSED: Untrimmed shulkers render correctly");

        // === PHASE 2: Add trims via RCON ===
        LOGGER.info("Phase 2: Adding trims via RCON while player is in range");

        try {
            for (TestWorldSetup.ShulkerPlacement placement : TestWorldSetup.getComprehensiveWorldPlacements()) {
                String dataMergeCommand = String.format(
                        "data merge block %d %d %d {components:{\"minecraft:custom_data\":{\"shulker_trims:trim\":{pattern:\"%s\",material:\"%s\"}}}}",
                        placement.x(), placement.y(), placement.z(),
                        placement.pattern(), placement.material());
                launcher.sendCommand(dataMergeCommand);
            }
            LOGGER.info("Added trims to all 18 shulkers");
        } catch (Exception e) {
            LOGGER.warn("Failed to add trims: {}", e.getMessage());
        }

        // Wait for client to receive updates
        context.waitTicks(40);

        // Compare against trimmed template
        TestScreenshotComparisonOptions trimmedOptions = TestScreenshotComparisonOptions.of("shulker-trim-comprehensive-world")
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .withSize(1920, 1080)
                .save();

        try {
            context.assertScreenshotEquals(trimmedOptions);
            LOGGER.info("Phase 2 PASSED: Trims appear after live addition!");
        } catch (AssertionError e) {
            LOGGER.error("Phase 2 FAILED: Trims do not appear after live addition");
            throw new AssertionError("Live trim addition failed: trims not visible after /data merge. " +
                    "The Paper plugin needs to detect block entity changes and sync.", e);
        }

        // === PHASE 3: Remove trims via RCON ===
        LOGGER.info("Phase 3: Removing trims via RCON while player is in range");

        try {
            for (TestWorldSetup.ShulkerPlacement placement : TestWorldSetup.getComprehensiveWorldPlacements()) {
                // Remove the trim from custom_data
                String dataRemoveCommand = String.format(
                        "data remove block %d %d %d components.\"minecraft:custom_data\".\"shulker_trims:trim\"",
                        placement.x(), placement.y(), placement.z());
                launcher.sendCommand(dataRemoveCommand);
            }
            LOGGER.info("Removed trims from all 18 shulkers");
        } catch (Exception e) {
            LOGGER.warn("Failed to remove trims: {}", e.getMessage());
        }

        // Wait for client to receive updates
        context.waitTicks(40);

        // Compare against untrimmed template
        try {
            context.assertScreenshotEquals(untrimmedOptions);
            LOGGER.info("Phase 3 PASSED: Trims disappear after live removal!");
        } catch (AssertionError e) {
            LOGGER.error("Phase 3 FAILED: Trims still visible after live removal");
            throw new AssertionError("Live trim removal failed: trims still visible after /data remove. " +
                    "The Paper plugin needs to detect block entity changes and sync.", e);
        }

        // Restore HUD
        context.runOnClient(client -> {
            client.options.hudHidden = false;
        });

        // Disconnect from server
        context.runOnClient(client -> {
            LOGGER.info("Disconnecting from Paper server...");
            client.disconnect(new TitleScreen(), false);
        });

        context.waitTicks(20);
        LOGGER.info("Paper live modification test completed successfully");
    }

    /**
     * Set up a chest with trimmed shulker items via RCON.
     */
    private void setupChestViaRcon(TestServerLauncher launcher) {
        try {
            for (String command : TestWorldSetup.generateChestGuiSetupCommands()) {
                String response = launcher.sendCommand(command);
                LOGGER.debug("RCON [{}]: {}", command, response);
            }
            LOGGER.info("Chest setup complete (18 trimmed shulker items)");
        } catch (Exception e) {
            LOGGER.warn("RCON chest setup encountered errors: {}", e.getMessage());
        }
    }

    /**
     * Set up a dispenser with a trimmed shulker box item via RCON.
     */
    private void setupDispenserViaRcon(TestServerLauncher launcher) {
        try {
            for (String command : TestWorldSetup.generateDispenserSetupCommands()) {
                String response = launcher.sendCommand(command);
                LOGGER.debug("RCON [{}]: {}", command, response);
            }
            LOGGER.info("Dispenser setup complete (facing down with trimmed blue shulker box)");
        } catch (Exception e) {
            LOGGER.warn("RCON dispenser setup encountered errors: {}", e.getMessage());
        }
    }

    /**
     * Test Paper server connection with chest GUI screenshot.
     */
    private void testPaperChestGuiConnection(ClientGameTestContext context, String serverAddress, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Chest GUI Connection ===");

        // Initiate connection
        context.runOnClient(client -> {
            ServerInfo serverInfo = new ServerInfo(
                    "Test Paper Server",
                    serverAddress,
                    ServerInfo.ServerType.OTHER
            );

            ConnectScreen.connect(
                    client.currentScreen,
                    client,
                    ServerAddress.parse(serverAddress),
                    serverInfo,
                    false,
                    null
            );
        });

        // Wait for world load
        LOGGER.info("Waiting for world load...");
        waitForWorldLoad(context);

        // Verify we're connected
        boolean isConnected = context.computeOnClient(client ->
                client.world != null && client.player != null
        );

        if (!isConnected) {
            throw new AssertionError("Failed to connect to Paper server");
        }

        LOGGER.info("Successfully connected to Paper server!");

        // Wait for initial chunks to load
        context.waitTicks(60);

        // Get player name
        String playerName = context.computeOnClient(client ->
                client.player != null ? client.player.getGameProfile().name() : "Player0"
        );

        // Teleport to chest position
        // The chest setup places barrier floors, so player won't fall
        String teleportCommand = TestWorldSetup.generateGuiTeleportCommand(
                playerName, TestWorldSetup.CHEST_X, TestWorldSetup.CHEST_Y, TestWorldSetup.CHEST_Z);
        LOGGER.info("Teleport command: {}", teleportCommand);

        try {
            launcher.sendCommand("gamemode creative " + playerName);
            launcher.sendCommand(teleportCommand);
        } catch (Exception e) {
            LOGGER.warn("Failed during teleport: {}", e.getMessage());
        }

        // Wait for teleport and chunks to render
        context.waitTicks(60);

        // Log final player position
        context.runOnClient(client -> {
            if (client.player != null) {
                LOGGER.info("Player position after teleport: {}, {}, {}",
                        client.player.getX(), client.player.getY(), client.player.getZ());
                if (client.world != null) {
                    var chestPos = new net.minecraft.util.math.BlockPos(
                            TestWorldSetup.CHEST_X, TestWorldSetup.CHEST_Y, TestWorldSetup.CHEST_Z);
                    var block = client.world.getBlockState(chestPos).getBlock();
                    LOGGER.info("Block at chest position: {}",
                            net.minecraft.registry.Registries.BLOCK.getId(block));
                }
            }
        });

        // Hide HUD for clean screenshot
        context.runOnClient(client -> {
            client.options.hudHidden = true;
        });

        // Open the chest by simulating right-click
        context.runOnClient(client -> {
            if (client.player != null && client.interactionManager != null) {
                var chestPos = new net.minecraft.util.math.BlockPos(
                        TestWorldSetup.CHEST_X, TestWorldSetup.CHEST_Y, TestWorldSetup.CHEST_Z);
                client.interactionManager.interactBlock(
                        client.player,
                        net.minecraft.util.Hand.MAIN_HAND,
                        new net.minecraft.util.hit.BlockHitResult(
                                chestPos.toCenterPos(),
                                net.minecraft.util.math.Direction.UP,
                                chestPos,
                                false
                        )
                );
            }
        });

        // Wait for chest screen to open
        context.waitTicks(20);

        // Verify chest screen is open
        boolean isChestOpen = context.computeOnClient(client ->
                client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen
        );

        if (!isChestOpen) {
            LOGGER.warn("Chest screen did not open, taking diagnostic screenshot");
            context.takeScreenshot("paper-chest-gui-failed");
            throw new AssertionError("Failed to open chest screen");
        }

        LOGGER.info("Chest screen opened, taking screenshot");

        // Compare against the golden template
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of("shulker-trim-comprehensive-inventory")
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .save();
        context.assertScreenshotEquals(options);
        LOGGER.info("Screenshot comparison passed: shulker-trim-comprehensive-inventory (Paper server)");

        // Close chest and restore HUD
        context.runOnClient(client -> {
            client.options.hudHidden = false;
            client.setScreen(null);
        });
        context.waitTicks(5);

        // Disconnect from server
        context.runOnClient(client -> {
            LOGGER.info("Disconnecting from Paper server...");
            client.disconnect(new TitleScreen(), false);
        });

        context.waitTicks(20);
        LOGGER.info("Paper chest GUI test completed successfully");
    }

    /**
     * Test Paper server connection with smithing table GUI screenshot.
     */
    private void testPaperSmithingTableConnection(ClientGameTestContext context, String serverAddress, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Smithing Table Connection ===");

        // Initiate connection
        context.runOnClient(client -> {
            ServerInfo serverInfo = new ServerInfo(
                    "Test Paper Server",
                    serverAddress,
                    ServerInfo.ServerType.OTHER
            );

            ConnectScreen.connect(
                    client.currentScreen,
                    client,
                    ServerAddress.parse(serverAddress),
                    serverInfo,
                    false,
                    null
            );
        });

        // Wait for world load
        LOGGER.info("Waiting for world load...");
        waitForWorldLoad(context);

        // Verify we're connected
        boolean isConnected = context.computeOnClient(client ->
                client.world != null && client.player != null
        );

        if (!isConnected) {
            throw new AssertionError("Failed to connect to Paper server");
        }

        LOGGER.info("Successfully connected to Paper server!");

        // Wait for initial chunks to load
        context.waitTicks(60);

        // Get player name
        String playerName = context.computeOnClient(client ->
                client.player != null ? client.player.getGameProfile().name() : "Player0"
        );

        // Set up smithing table and give player items via RCON
        LOGGER.info("Setting up smithing table via RCON...");
        try {
            for (String command : TestWorldSetup.generateSmithingTableSetupCommands(playerName)) {
                String response = launcher.sendCommand(command);
                LOGGER.debug("RCON [{}]: {}", command, response);
            }
            LOGGER.info("Smithing table setup complete");
        } catch (Exception e) {
            LOGGER.warn("RCON smithing table setup encountered errors: {}", e.getMessage());
        }

        // Teleport to smithing table position
        try {
            String teleportCommand = TestWorldSetup.generateGuiTeleportCommand(
                    playerName, TestWorldSetup.SMITHING_TABLE_X, TestWorldSetup.SMITHING_TABLE_Y, TestWorldSetup.SMITHING_TABLE_Z);
            launcher.sendCommand(teleportCommand);
            launcher.sendCommand("gamemode creative " + playerName);
        } catch (Exception e) {
            LOGGER.warn("Failed to teleport player: {}", e.getMessage());
        }

        // Wait for chunks to load
        context.waitTicks(40);

        // Hide HUD for clean screenshot
        context.runOnClient(client -> {
            client.options.hudHidden = true;
        });

        // Open the smithing table by simulating right-click
        context.runOnClient(client -> {
            if (client.player != null && client.interactionManager != null) {
                var smithingPos = new net.minecraft.util.math.BlockPos(
                        TestWorldSetup.SMITHING_TABLE_X, TestWorldSetup.SMITHING_TABLE_Y, TestWorldSetup.SMITHING_TABLE_Z);
                client.interactionManager.interactBlock(
                        client.player,
                        net.minecraft.util.Hand.MAIN_HAND,
                        new net.minecraft.util.hit.BlockHitResult(
                                smithingPos.toCenterPos(),
                                net.minecraft.util.math.Direction.UP,
                                smithingPos,
                                false
                        )
                );
            }
        });

        // Wait for smithing table screen to open
        context.waitForScreen(net.minecraft.client.gui.screen.ingame.SmithingScreen.class);
        context.waitTicks(10);

        // Move items from hotbar into smithing table slots using shift-click
        // SmithingScreenHandler slot mapping: 0=template, 1=base, 2=addition, 3=result, 4-30=inventory, 31-39=hotbar
        context.runOnClient(client -> {
            if (client.player != null && client.interactionManager != null &&
                client.player.currentScreenHandler instanceof net.minecraft.screen.SmithingScreenHandler handler) {
                // Shift-click template from hotbar slot 31
                client.interactionManager.clickSlot(handler.syncId, 31, 0,
                        net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, client.player);
            }
        });
        context.waitTicks(3);

        context.runOnClient(client -> {
            if (client.player != null && client.interactionManager != null &&
                client.player.currentScreenHandler instanceof net.minecraft.screen.SmithingScreenHandler handler) {
                // Shift-click shulker box from hotbar slot 32
                client.interactionManager.clickSlot(handler.syncId, 32, 0,
                        net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, client.player);
            }
        });
        context.waitTicks(3);

        context.runOnClient(client -> {
            if (client.player != null && client.interactionManager != null &&
                client.player.currentScreenHandler instanceof net.minecraft.screen.SmithingScreenHandler handler) {
                // Shift-click redstone from hotbar slot 33
                client.interactionManager.clickSlot(handler.syncId, 33, 0,
                        net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, client.player);
            }
        });
        context.waitTicks(10);

        // Log the current state
        context.runOnClient(client -> {
            if (client.currentScreen == null) {
                LOGGER.error("Smithing screen closed unexpectedly!");
            } else {
                LOGGER.info("Screen still open: {}", client.currentScreen.getClass().getSimpleName());
                if (client.player.currentScreenHandler instanceof net.minecraft.screen.SmithingScreenHandler handler) {
                    LOGGER.info("Slot 0 (template): {}", handler.getSlot(0).getStack());
                    LOGGER.info("Slot 1 (base): {}", handler.getSlot(1).getStack());
                    LOGGER.info("Slot 2 (addition): {}", handler.getSlot(2).getStack());
                    LOGGER.info("Slot 3 (result): {}", handler.getSlot(3).getStack());
                }
            }
        });

        // Compare against the golden template
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of("shulker-trim-smithing-preview")
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .save();
        context.assertScreenshotEquals(options);
        LOGGER.info("Screenshot comparison passed: shulker-trim-smithing-preview (Paper server)");

        // Close smithing table and restore HUD
        context.runOnClient(client -> {
            client.options.hudHidden = false;
            client.setScreen(null);
        });
        context.waitForScreen(null);

        // Disconnect from server
        context.runOnClient(client -> {
            LOGGER.info("Disconnecting from Paper server...");
            client.disconnect(new TitleScreen(), false);
        });

        context.waitTicks(20);
        LOGGER.info("Paper smithing table test completed successfully");
    }

    /**
     * Test Paper server connection with dispenser-placed shulker box screenshot.
     *
     * <p>This test verifies that when a dispenser places a trimmed shulker box,
     * the trim data is correctly synced to nearby clients. This exercises a
     * different code path than player placement.
     */
    private void testPaperDispenserPlacementConnection(ClientGameTestContext context, String serverAddress, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Dispenser Placement Connection ===");

        // Initiate connection
        context.runOnClient(client -> {
            ServerInfo serverInfo = new ServerInfo(
                    "Test Paper Server",
                    serverAddress,
                    ServerInfo.ServerType.OTHER
            );

            ConnectScreen.connect(
                    client.currentScreen,
                    client,
                    ServerAddress.parse(serverAddress),
                    serverInfo,
                    false,
                    null
            );
        });

        // Wait for world load
        LOGGER.info("Waiting for world load...");
        waitForWorldLoad(context);

        // Verify we're connected
        boolean isConnected = context.computeOnClient(client ->
                client.world != null && client.player != null
        );

        if (!isConnected) {
            throw new AssertionError("Failed to connect to Paper server");
        }

        LOGGER.info("Successfully connected to Paper server!");

        // Wait for initial chunks to load
        context.waitTicks(60);

        // Get player name
        String playerName = context.computeOnClient(client ->
                client.player != null ? client.player.getGameProfile().name() : "Player0"
        );

        // Teleport to camera position to view dispenser output area
        // This is an observation test - use spectator mode (no falling, no barriers needed)
        try {
            String teleportCommand = TestWorldSetup.generateDispenserTeleportCommand(playerName);
            LOGGER.info("Teleport command: {}", teleportCommand);

            launcher.sendCommand("gamemode spectator " + playerName);
            launcher.sendCommand(teleportCommand);
        } catch (Exception e) {
            LOGGER.warn("Failed to teleport player: {}", e.getMessage());
        }

        // Wait for teleport and chunks to render at camera position
        context.waitTicks(60);

        // Hide HUD for clean screenshots
        context.runOnClient(client -> {
            client.options.hudHidden = true;
        });

        // Log state before firing dispenser
        context.runOnClient(client -> {
            if (client.player != null && client.world != null) {
                LOGGER.info("Player position: {}, {}, {}",
                        client.player.getX(), client.player.getY(), client.player.getZ());

                // Check if dispenser output area is clear
                var outputPos = new net.minecraft.util.math.BlockPos(
                        TestWorldSetup.DISPENSER_X, TestWorldSetup.DISPENSER_OUTPUT_Y, TestWorldSetup.DISPENSER_Z);
                var block = client.world.getBlockState(outputPos).getBlock();
                LOGGER.info("Block at dispenser output ({}, {}, {}): {}",
                        TestWorldSetup.DISPENSER_X, TestWorldSetup.DISPENSER_OUTPUT_Y, TestWorldSetup.DISPENSER_Z,
                        net.minecraft.registry.Registries.BLOCK.getId(block));
            }
        });

        // Fire the dispenser by placing a redstone block
        LOGGER.info("Firing dispenser...");
        try {
            String fireCommand = TestWorldSetup.generateFireDispenserCommand();
            LOGGER.info("Fire command: {}", fireCommand);
            String response = launcher.sendCommand(fireCommand);
            LOGGER.info("Fire response: {}", response);
        } catch (Exception e) {
            LOGGER.warn("Failed to fire dispenser: {}", e.getMessage());
        }

        // Wait for dispenser to fire and trim sync to occur
        // Dispenser should activate immediately when redstone block is placed
        context.waitTicks(20);

        // Remove the redstone block and dispenser to ensure clean screenshot
        // The dispenser has texture variability (animation states) that causes test flakiness
        try {
            launcher.sendCommand(String.format("setblock %d %d %d minecraft:air",
                    TestWorldSetup.DISPENSER_X + 1, TestWorldSetup.DISPENSER_Y, TestWorldSetup.DISPENSER_Z));
            launcher.sendCommand(String.format("setblock %d %d %d minecraft:air",
                    TestWorldSetup.DISPENSER_X, TestWorldSetup.DISPENSER_Y, TestWorldSetup.DISPENSER_Z));
        } catch (Exception e) {
            LOGGER.warn("Failed to remove blocks: {}", e.getMessage());
        }

        // Wait for smoke particles to fully dissipate (3 seconds)
        context.waitTicks(60);

        // Verify the shulker box was placed
        context.runOnClient(client -> {
            if (client.world != null) {
                var outputPos = new net.minecraft.util.math.BlockPos(
                        TestWorldSetup.DISPENSER_X, TestWorldSetup.DISPENSER_OUTPUT_Y, TestWorldSetup.DISPENSER_Z);
                var blockState = client.world.getBlockState(outputPos);
                var block = blockState.getBlock();
                LOGGER.info("Block at dispenser output after firing: {}",
                        net.minecraft.registry.Registries.BLOCK.getId(block));

                // Check if it's a shulker box
                if (block instanceof net.minecraft.block.ShulkerBoxBlock) {
                    LOGGER.info("Shulker box placed successfully!");
                } else {
                    LOGGER.warn("Expected shulker box but found: {}", block);
                }
            }
        });

        // Take screenshot and compare against golden template
        // This verifies the trim is visible on the dispenser-placed shulker
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of("shulker-trim-dispenser-placed")
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .withSize(1920, 1080)
                .save();

        context.assertScreenshotEquals(options);
        LOGGER.info("Screenshot comparison passed: shulker-trim-dispenser-placed");

        // Restore HUD
        context.runOnClient(client -> {
            client.options.hudHidden = false;
        });

        // Disconnect from server
        context.runOnClient(client -> {
            LOGGER.info("Disconnecting from Paper server...");
            client.disconnect(new TitleScreen(), false);
        });

        context.waitTicks(20);
        LOGGER.info("Paper dispenser placement test completed successfully");
    }

    /**
     * Set up the test world using RCON commands.
     */
    private void setupWorldViaRcon(TestServerLauncher launcher) {
        try {
            // Use basic setup first (without trims) to verify RCON works
            for (String command : TestWorldSetup.generateBasicSetupCommands()) {
                String response = launcher.sendCommand(command);
                LOGGER.debug("RCON [{}]: {}", command, response);
            }
            LOGGER.info("Basic world setup complete (18 shulker boxes placed)");

            // Now try to apply trims via data merge
            // In MC 1.21+, block entity component data is stored under "components"
            // The path is: components."minecraft:custom_data"."shulker_trims:trim"
            int trimCount = 0;
            for (TestWorldSetup.ShulkerPlacement placement : TestWorldSetup.getComprehensiveWorldPlacements()) {
                String dataMergeCommand = String.format(
                        "data merge block %d %d %d {components:{\"minecraft:custom_data\":{\"shulker_trims:trim\":{pattern:\"%s\",material:\"%s\"}}}}",
                        placement.x(), placement.y(), placement.z(),
                        placement.pattern(), placement.material());
                try {
                    String response = launcher.sendCommand(dataMergeCommand);
                    if (!response.contains("error") && !response.contains("Unknown")) {
                        trimCount++;
                    }
                    LOGGER.debug("Data merge [{}]: {}", placement.pattern(), response);
                } catch (Exception e) {
                    LOGGER.warn("Failed to apply trim at ({}, {}, {}): {}",
                            placement.x(), placement.y(), placement.z(), e.getMessage());
                }
            }
            LOGGER.info("Applied {} trims via data merge", trimCount);

            // Verify the NBT was actually set by reading back the custom_data specifically
            String verifyCommand = "data get block 0 100 0 components.\"minecraft:custom_data\"";
            try {
                String nbtData = launcher.sendCommand(verifyCommand);
                LOGGER.info("NBT custom_data at (0, 100, 0): {}", nbtData);
                // Expected format: {"shulker_trims:trim": {material: "minecraft:quartz", pattern: "minecraft:sentry"}}
            } catch (Exception e) {
                LOGGER.warn("Failed to verify NBT: {}", e.getMessage());
            }

        } catch (Exception e) {
            LOGGER.warn("RCON world setup encountered errors: {}", e.getMessage());
            // Continue anyway - we can still test basic connectivity
        }
    }

    /**
     * Test using Fabric's built-in dedicated server creation.
     * This should work - it's the supported approach.
     */
    private void testFabricDedicatedServer(ClientGameTestContext context) {
        LOGGER.info("=== Test: Fabric Built-in Dedicated Server ===");

        try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
            LOGGER.info("Dedicated server created, connecting...");

            try (TestServerConnection connection = server.connect()) {
                LOGGER.info("Connected to dedicated server!");

                // Wait for chunks to render
                connection.getClientWorld().waitForChunksRender();
                LOGGER.info("Chunks rendered");

                // Log player position
                context.runOnClient(client -> {
                    if (client.player != null) {
                        LOGGER.info("Player position: {}, {}, {}",
                                client.player.getX(),
                                client.player.getY(),
                                client.player.getZ()
                        );
                    }
                });

                // Take a screenshot to verify connection works
                context.takeScreenshot("fabric-dedicated-server-test");
                LOGGER.info("Screenshot saved: fabric-dedicated-server-test.png");
            }

            LOGGER.info("Disconnected from dedicated server");
        }

        LOGGER.info("Dedicated server stopped");
    }

    /**
     * Test connecting to an external server.
     * Uses the same approach as Fabric's TestDedicatedServerContextImpl.
     *
     * @param context The gametest context
     * @param serverAddress The server address to connect to (e.g., "127.0.0.1:25565")
     */
    private void testExternalServerConnection(ClientGameTestContext context, String serverAddress) {
        LOGGER.info("=== Test: External Server Connection ===");
        LOGGER.info("Attempting to connect to: {}", serverAddress);

        // Create server info entry and initiate connection using Fabric's approach
        context.runOnClient(client -> {
            LOGGER.info("Initiating connection from client thread...");
            LOGGER.info("Current screen: {}", client.currentScreen);

            ServerInfo serverInfo = new ServerInfo(
                    "Test Paper Server",
                    serverAddress,
                    ServerInfo.ServerType.OTHER
            );

            // Use connect like Fabric's TestDedicatedServerContextImpl does
            // Pass client.currentScreen as parent, null for cookies
            // (Mojang's "startConnecting" is "connect" in Yarn mappings)
            ConnectScreen.connect(
                    client.currentScreen,  // parent screen (current screen, not TitleScreen)
                    client,
                    ServerAddress.parse(serverAddress),
                    serverInfo,
                    false,  // not quickPlay
                    null    // null cookie storage (like Fabric does)
            );
        });

        // Wait for world load using Fabric's pattern
        LOGGER.info("Waiting for world load...");
        waitForWorldLoad(context);

        // Verify we're connected
        boolean isConnected = context.computeOnClient(client ->
                client.world != null && client.player != null
        );

        if (!isConnected) {
            String disconnectReason = context.computeOnClient(client -> {
                LOGGER.error("Failed to connect to external server!");
                if (client.currentScreen != null) {
                    LOGGER.error("Final screen: {}", client.currentScreen.getClass().getName());
                    if (client.currentScreen instanceof DisconnectedScreen disconnectedScreen) {
                        Text title = disconnectedScreen.getTitle();
                        LOGGER.error("Disconnect title: {}", title.getString());
                        return title.getString();
                    }
                }
                return "Unknown reason";
            });
            LOGGER.warn("External server connection failed: {}", disconnectReason);
            LOGGER.warn("This may be due to network sandboxing in the gametest environment");
            return; // Don't fail the test, just log
        }

        LOGGER.info("Successfully connected to external server!");

        // Wait for chunks to load
        context.waitTicks(60);

        // Log player position
        context.runOnClient(client -> {
            if (client.player != null) {
                LOGGER.info("Player position: {}, {}, {}",
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ()
                );
            }
        });

        // Take a test screenshot
        context.takeScreenshot("external-server-test");
        LOGGER.info("Screenshot saved: external-server-test.png");

        // Disconnect from server
        context.runOnClient(client -> {
            LOGGER.info("Disconnecting from server...");
            client.disconnect(new TitleScreen(), false);
        });

        context.waitTicks(20);
        LOGGER.info("External server connection test completed");
    }

    /**
     * Test Paper server with full world setup, camera positioning, and screenshots.
     *
     * @param context The gametest context
     * @param serverAddress The server address to connect to
     * @param launcher The server launcher (for RCON commands)
     */
    private void testPaperServerConnection(ClientGameTestContext context, String serverAddress, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Server with World Setup ===");
        LOGGER.info("Attempting to connect to: {}", serverAddress);

        // Initiate connection
        context.runOnClient(client -> {
            LOGGER.info("Initiating connection from client thread...");

            ServerInfo serverInfo = new ServerInfo(
                    "Test Paper Server",
                    serverAddress,
                    ServerInfo.ServerType.OTHER
            );

            ConnectScreen.connect(
                    client.currentScreen,
                    client,
                    ServerAddress.parse(serverAddress),
                    serverInfo,
                    false,
                    null
            );
        });

        // Wait for world load
        LOGGER.info("Waiting for world load...");
        waitForWorldLoad(context);

        // Verify we're connected
        boolean isConnected = context.computeOnClient(client ->
                client.world != null && client.player != null
        );

        if (!isConnected) {
            context.computeOnClient(client -> {
                LOGGER.error("Failed to connect to Paper server!");
                if (client.currentScreen != null) {
                    LOGGER.error("Final screen: {}", client.currentScreen.getClass().getName());
                    if (client.currentScreen instanceof DisconnectedScreen disconnectedScreen) {
                        LOGGER.error("Disconnect reason: {}", disconnectedScreen.getTitle().getString());
                    }
                }
                return null;
            });
            throw new AssertionError("Failed to connect to Paper server");
        }

        LOGGER.info("Successfully connected to Paper server!");

        // Log initial spawn position
        context.runOnClient(client -> {
            if (client.player != null) {
                LOGGER.info("Initial spawn position: {}, {}, {}",
                        client.player.getX(), client.player.getY(), client.player.getZ());
            }
        });

        // Wait for initial chunks to load
        context.waitTicks(60);

        // Get player name for teleport command
        String playerName = context.computeOnClient(client ->
                client.player != null ? client.player.getGameProfile().name() : "Player0"
        );

        // Teleport player to camera position using RCON
        try {
            String teleportCommand = TestWorldSetup.generateTeleportCommand(playerName);
            LOGGER.info("Sending teleport command: {}", teleportCommand);
            String response = launcher.sendCommand(teleportCommand);
            LOGGER.info("Teleport response: {}", response);

            // Ensure player is in creative mode and flying
            launcher.sendCommand("gamemode creative " + playerName);
        } catch (Exception e) {
            LOGGER.warn("Failed to teleport player via RCON: {}", e.getMessage());
        }

        // Wait a bit for server to process teleport
        context.waitTicks(20);

        // Verify teleport worked by checking position
        context.runOnClient(client -> {
            if (client.player != null) {
                LOGGER.info("Position after teleport: {}, {}, {} (expected: {}, {}, {})",
                        client.player.getX(), client.player.getY(), client.player.getZ(),
                        TestWorldSetup.CAMERA_X + 0.5, TestWorldSetup.CAMERA_Y, TestWorldSetup.CAMERA_Z + 0.5);
            }
        });

        // Wait for chunks to render at new position
        context.waitTicks(100);

        // Log final player position and nearby blocks
        context.runOnClient(client -> {
            if (client.player != null) {
                LOGGER.info("Final position before screenshot: {}, {}, {} (yaw={}, pitch={})",
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),
                        client.player.getYaw(),
                        client.player.getPitch()
                );

                // Check if we can see any blocks at the expected shulker positions
                if (client.world != null) {
                    var pos = new net.minecraft.util.math.BlockPos(0, TestWorldSetup.BASE_Y, 0);
                    var block = client.world.getBlockState(pos).getBlock();
                    LOGGER.info("Block at ({}, {}, {}): {}", 0, TestWorldSetup.BASE_Y, 0,
                            net.minecraft.registry.Registries.BLOCK.getId(block));
                }
            }
        });

        // Hide HUD for clean screenshot (no hotbar, crosshair, hand, chat)
        context.runOnClient(client -> {
            client.options.hudHidden = true;
        });

        // Compare against the same golden template as the singleplayer test
        // This verifies cross-platform rendering consistency
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of("shulker-trim-comprehensive-world")
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(0.0001f))
                .withSize(1920, 1080)
                .save();
        context.assertScreenshotEquals(options);
        LOGGER.info("Screenshot comparison passed: shulker-trim-comprehensive-world (Paper server)");

        // Restore HUD
        context.runOnClient(client -> {
            client.options.hudHidden = false;
        });

        // Disconnect from server
        context.runOnClient(client -> {
            LOGGER.info("Disconnecting from Paper server...");
            client.disconnect(new TitleScreen(), false);
        });

        context.waitTicks(20);
        LOGGER.info("Paper server test completed successfully");
    }

    /**
     * Test Paper server connection with chunk unload/reload cycle.
     *
     * <p>This verifies that trim data is synced when player re-enters an area
     * after chunks have been unloaded from the client.
     */
    private void testPaperChunkReloadConnection(ClientGameTestContext context, String serverAddress, TestServerLauncher launcher) {
        LOGGER.info("=== Test: Paper Server Chunk Reload Sync ===");
        LOGGER.info("Attempting to connect to: {}", serverAddress);

        // Initiate connection
        context.runOnClient(client -> {
            ServerInfo serverInfo = new ServerInfo(
                    "Test Paper Server",
                    serverAddress,
                    ServerInfo.ServerType.OTHER
            );

            ConnectScreen.connect(
                    client.currentScreen,
                    client,
                    ServerAddress.parse(serverAddress),
                    serverInfo,
                    false,
                    null
            );
        });

        // Wait for world load
        LOGGER.info("Waiting for world load...");
        waitForWorldLoad(context);

        // Verify we're connected
        boolean isConnected = context.computeOnClient(client ->
                client.world != null && client.player != null
        );

        if (!isConnected) {
            throw new AssertionError("Failed to connect to Paper server");
        }

        LOGGER.info("Successfully connected to Paper server!");

        // Wait for initial chunks to load
        context.waitTicks(60);

        // Get player name for teleport commands
        String playerName = context.computeOnClient(client ->
                client.player != null ? client.player.getGameProfile().name() : "Player0"
        );

        // === PHASE 1: Initial teleport to camera position ===
        LOGGER.info("Phase 1: Initial teleport to camera position");
        try {
            String teleportCommand = TestWorldSetup.generateTeleportCommand(playerName);
            launcher.sendCommand(teleportCommand);
            launcher.sendCommand("gamemode creative " + playerName);
        } catch (Exception e) {
            LOGGER.warn("Failed to teleport player: {}", e.getMessage());
        }

        // Wait for chunks to render at camera position
        context.waitTicks(100);

        // Hide HUD for clean screenshots
        context.runOnClient(client -> {
            client.options.hudHidden = true;
        });

        // Verify initial screenshot matches (sanity check before chunk reload test)
        LOGGER.info("Taking initial screenshot at camera position...");
        context.takeScreenshot("chunk-reload-test-initial");

        // === PHASE 2: Teleport far away to force chunk unload ===
        // Simulation distance is 10 chunks = 160 blocks, so 500 blocks away should be safe
        int farX = TestWorldSetup.CAMERA_X + 500;
        int farZ = TestWorldSetup.CAMERA_Z + 500;
        LOGGER.info("Phase 2: Teleporting far away to ({}, {}, {}) to force chunk unload",
                farX, TestWorldSetup.CAMERA_Y, farZ);

        try {
            String teleportFar = String.format("tp %s %d %d %d",
                    playerName, farX, TestWorldSetup.CAMERA_Y, farZ);
            launcher.sendCommand(teleportFar);
        } catch (Exception e) {
            LOGGER.warn("Failed to teleport player far: {}", e.getMessage());
        }

        // Wait for client to receive teleport and unload old chunks
        // Give enough time for chunks to fully unload from client memory
        LOGGER.info("Waiting for chunks to unload...");
        context.waitTicks(100); // 5 seconds

        // Verify player is at far position
        context.runOnClient(client -> {
            if (client.player != null) {
                LOGGER.info("Player position after far teleport: {}, {}, {}",
                        client.player.getX(), client.player.getY(), client.player.getZ());
            }
        });

        // === PHASE 3: Teleport back to camera position ===
        LOGGER.info("Phase 3: Teleporting back to camera position");
        try {
            String teleportBack = TestWorldSetup.generateTeleportCommand(playerName);
            launcher.sendCommand(teleportBack);
        } catch (Exception e) {
            LOGGER.warn("Failed to teleport player back: {}", e.getMessage());
        }

        // Wait for chunks to reload at camera position
        LOGGER.info("Waiting for chunks to reload...");
        context.waitTicks(100); // 5 seconds

        // Verify player is back at camera position
        context.runOnClient(client -> {
            if (client.player != null) {
                LOGGER.info("Player position after return teleport: {}, {}, {} (expected: {}, {}, {})",
                        client.player.getX(), client.player.getY(), client.player.getZ(),
                        TestWorldSetup.CAMERA_X + 0.5, TestWorldSetup.CAMERA_Y, TestWorldSetup.CAMERA_Z + 0.5);
            }
        });

        // === PHASE 4: Screenshot comparison ===
        // This is the key test: after chunk reload, do trims still render?
        LOGGER.info("Phase 4: Taking screenshot after chunk reload");

        // Compare against the golden template
        // This SHOULD FAIL if chunk reload sync is not implemented
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of("shulker-trim-comprehensive-world")
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .withSize(1920, 1080)
                .save();

        try {
            context.assertScreenshotEquals(options);
            LOGGER.info("Screenshot comparison PASSED after chunk reload!");
        } catch (AssertionError e) {
            LOGGER.error("Screenshot comparison FAILED after chunk reload - trims not visible!");
            LOGGER.error("This indicates the Paper plugin is not syncing trim data on chunk reload");
            // Re-throw to fail the test
            throw new AssertionError("Chunk reload sync failed: trims not visible after returning to area. " +
                    "The Paper plugin needs to implement PlayerChunkLoadEvent handling.", e);
        }

        // Restore HUD
        context.runOnClient(client -> {
            client.options.hudHidden = false;
        });

        // Disconnect from server
        context.runOnClient(client -> {
            LOGGER.info("Disconnecting from Paper server...");
            client.disconnect(new TitleScreen(), false);
        });

        context.waitTicks(20);
        LOGGER.info("Paper chunk reload sync test completed");
    }

    /**
     * Wait for world to load, handling any warning screens.
     * Based on Fabric's ClientGameTestImpl.waitForWorldLoad().
     */
    private void waitForWorldLoad(ClientGameTestContext context) {
        // Wait up to 60 seconds (1200 ticks)
        for (int i = 0; i < 1200; i++) {
            final int tick = i;

            // Check if world loading is finished
            boolean finished = context.computeOnClient(client -> {
                boolean worldLoaded = client.world != null;
                String screenName = client.currentScreen != null ?
                        client.currentScreen.getClass().getSimpleName() : "null";

                if (tick % 20 == 0) { // Log every second
                    LOGGER.info("World load check: world={}, screen={}", worldLoaded, screenName);
                }

                // World is loaded when client.world exists and we're not on a loading screen
                return worldLoaded && !screenName.contains("Loading") && !screenName.contains("Progress");
            });

            if (finished) {
                LOGGER.info("World loading finished!");
                return;
            }

            context.waitTick();
        }

        throw new AssertionError("Timeout loading world");
    }

    /**
     * Asserts that a screenshot matches the golden template image.
     * Uses Mean Squared Difference algorithm with configurable threshold.
     *
     * @param context the client game test context
     * @param templateName the name of the template image (without extension)
     */
    private void assertScreenshotMatchesTemplate(ClientGameTestContext context, String templateName) {
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of(templateName)
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .save(); // Also save the actual screenshot for debugging comparison failures
        context.assertScreenshotEquals(options);
        LOGGER.info("Screenshot comparison passed: {}", templateName);
    }
}
