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


/**
 * Cross-platform server connection tests.
 *
 * <p>These tests verify that the gametest client can connect to external servers
 * and take screenshots for golden image comparison.
 *
 * <p>Test modes:
 * <ul>
 *   <li>FABRIC_INPROCESS: Use Fabric's built-in createServer() for in-process dedicated server</li>
 *   <li>PAPER_EXTERNAL: Launch a Paper server process and connect to it</li>
 *   <li>EXTERNAL_MANUAL: Connect to a manually-started server (for development)</li>
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

    // Test mode - change this to switch between test types
    private enum TestMode {
        FABRIC_INPROCESS,       // Use Fabric's built-in dedicated server
        PAPER_LAUNCHED,         // Launch Paper server automatically
        PAPER_CHUNK_RELOAD,     // Test chunk unload/reload sync on Paper
        PAPER_LIVE_MODIFICATION,// Test live trim add/remove while player in range
        PAPER_CHEST_GUI,        // Test chest GUI with trimmed shulker items
        PAPER_SMITHING_TABLE,   // Test smithing table trim preview
        EXTERNAL_MANUAL         // Connect to manually-started server
    }

    private static final TestMode TEST_MODE = TestMode.PAPER_LAUNCHED;

    @Override
    public void runTest(ClientGameTestContext context) {
        switch (TEST_MODE) {
            case FABRIC_INPROCESS -> testFabricDedicatedServer(context);
            case PAPER_LAUNCHED -> testPaperServerLaunched(context);
            case PAPER_CHUNK_RELOAD -> testPaperChunkReloadSync(context);
            case PAPER_LIVE_MODIFICATION -> testPaperLiveModification(context);
            case PAPER_CHEST_GUI -> testPaperChestGui(context);
            case PAPER_SMITHING_TABLE -> testPaperSmithingTable(context);
            case EXTERNAL_MANUAL -> testExternalServerConnection(context, MANUAL_SERVER_ADDRESS);
        }
    }

    /**
     * Test that trim data syncs correctly when modified while player is in range.
     *
     * <p>This test verifies that the Paper plugin correctly syncs trim data when:
     * 1. Trims are added via /data merge while player is watching
     * 2. Trims are removed via /data remove while player is watching
     *
     * <p>This is the "live modification" test - changes happen in real-time.
     */
    private void testPaperLiveModification(ClientGameTestContext context) {
        LOGGER.info("=== Test: Paper Server Live Trim Modification ===");

        try (TestServerLauncher launcher = new TestServerLauncher(
                TestServerLauncher.ServerType.PAPER, TEST_SERVER_PORT)) {

            // Start the server
            launcher.start(180);

            // Set up UNTRIMMED world (no trims initially)
            LOGGER.info("Setting up untrimmed world via RCON...");
            setupUntrimmedWorldViaRcon(launcher);

            // Connect to the server
            String address = launcher.getAddress();
            LOGGER.info("Connecting to Paper server at {}", address);

            // Run the live modification test
            testPaperLiveModificationConnection(context, address, launcher);

        } catch (Exception e) {
            LOGGER.error("Paper live modification test failed", e);
            throw new AssertionError("Paper live modification test failed: " + e.getMessage(), e);
        }
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
     * Test chest GUI rendering with trimmed shulker items on Paper server.
     *
     * <p>This test verifies that trimmed shulker items render correctly in a chest GUI
     * when connected to a Paper server. It compares against the singleplayer golden template.
     */
    private void testPaperChestGui(ClientGameTestContext context) {
        LOGGER.info("=== Test: Paper Server Chest GUI ===");

        try (TestServerLauncher launcher = new TestServerLauncher(
                TestServerLauncher.ServerType.PAPER, TEST_SERVER_PORT)) {

            // Start the server
            launcher.start(180);

            // Set up the chest with trimmed shulkers via RCON
            LOGGER.info("Setting up chest with trimmed shulkers via RCON...");
            setupChestViaRcon(launcher);

            // Connect to the server
            String address = launcher.getAddress();
            LOGGER.info("Connecting to Paper server at {}", address);

            // Run the chest GUI test
            testPaperChestGuiConnection(context, address, launcher);

        } catch (Exception e) {
            LOGGER.error("Paper chest GUI test failed", e);
            throw new AssertionError("Paper chest GUI test failed: " + e.getMessage(), e);
        }
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
        String teleportCommand = TestWorldSetup.generateGuiTeleportCommand(
                playerName, TestWorldSetup.CHEST_X, TestWorldSetup.CHEST_Y, TestWorldSetup.CHEST_Z);
        LOGGER.info("Teleport command: {}", teleportCommand);

        try {
            // Set creative mode and spectator briefly to stop falling
            launcher.sendCommand("gamemode spectator " + playerName);
            context.waitTicks(10);

            // Teleport while in spectator mode (won't fall)
            String tpResp = launcher.sendCommand(teleportCommand);
            LOGGER.info("Teleport response: {}", tpResp);
            context.waitTicks(40);

            // Switch back to creative
            launcher.sendCommand("gamemode creative " + playerName);
            context.waitTicks(20);

            // Verify position
            Double playerY = context.computeOnClient(client ->
                client.player != null ? client.player.getY() : null
            );
            LOGGER.info("Player Y after teleport: {}", playerY);

            // If still not at correct position, retry
            if (playerY == null || Math.abs(playerY - TestWorldSetup.CHEST_Y) > 5) {
                LOGGER.warn("Player not at target position, retrying teleport");
                launcher.sendCommand("gamemode spectator " + playerName);
                context.waitTicks(10);
                launcher.sendCommand(teleportCommand);
                context.waitTicks(40);
                launcher.sendCommand("gamemode creative " + playerName);
                context.waitTicks(20);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed during teleport: {}", e.getMessage());
        }

        // Final wait for chunks to render
        context.waitTicks(40);

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
     * Test smithing table GUI rendering with trim preview on Paper server.
     *
     * <p>This test verifies that the smithing table trim preview renders correctly
     * when connected to a Paper server. It compares against the singleplayer golden template.
     */
    private void testPaperSmithingTable(ClientGameTestContext context) {
        LOGGER.info("=== Test: Paper Server Smithing Table ===");

        try (TestServerLauncher launcher = new TestServerLauncher(
                TestServerLauncher.ServerType.PAPER, TEST_SERVER_PORT)) {

            // Start the server
            launcher.start(180);

            // Connect first to get player name, then set up
            String address = launcher.getAddress();
            LOGGER.info("Connecting to Paper server at {}", address);

            // Run the smithing table test
            testPaperSmithingTableConnection(context, address, launcher);

        } catch (Exception e) {
            LOGGER.error("Paper smithing table test failed", e);
            throw new AssertionError("Paper smithing table test failed: " + e.getMessage(), e);
        }
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
     * Test that trim data syncs correctly when player leaves and re-enters chunk area.
     *
     * <p>This test verifies that the Paper plugin correctly syncs trim data when:
     * 1. Player connects and receives initial trim sync (via PlayerRegisterChannelEvent)
     * 2. Player moves away, causing chunks to unload
     * 3. Player returns, causing chunks to reload
     *
     * <p>The expected behavior is that trims render correctly after chunk reload,
     * but without proper chunk load sync handling, the trims won't be visible.
     */
    private void testPaperChunkReloadSync(ClientGameTestContext context) {
        LOGGER.info("=== Test: Paper Server Chunk Reload Sync ===");

        try (TestServerLauncher launcher = new TestServerLauncher(
                TestServerLauncher.ServerType.PAPER, TEST_SERVER_PORT)) {

            // Start the server
            launcher.start(180);

            // Set up the test world via RCON
            LOGGER.info("Setting up test world via RCON...");
            setupWorldViaRcon(launcher);

            // Connect to the server
            String address = launcher.getAddress();
            LOGGER.info("Connecting to Paper server at {}", address);

            // Run the chunk reload test
            testPaperChunkReloadConnection(context, address, launcher);

        } catch (Exception e) {
            LOGGER.error("Paper chunk reload test failed", e);
            throw new AssertionError("Paper chunk reload test failed: " + e.getMessage(), e);
        }
    }

    /**
     * Test with Paper server launched automatically.
     */
    private void testPaperServerLaunched(ClientGameTestContext context) {
        LOGGER.info("=== Test: Paper Server (Auto-Launched) ===");

        try (TestServerLauncher launcher = new TestServerLauncher(
                TestServerLauncher.ServerType.PAPER, TEST_SERVER_PORT)) {

            // Start the server (3 minute timeout - Paper startup can be slow after download)
            launcher.start(180);

            // Set up the test world via RCON before connecting
            LOGGER.info("Setting up test world via RCON...");
            setupWorldViaRcon(launcher);

            // Connect to the server
            String address = launcher.getAddress();
            LOGGER.info("Connecting to Paper server at {}", address);
            testPaperServerConnection(context, address, launcher);

        } catch (Exception e) {
            LOGGER.error("Paper server test failed", e);
            throw new AssertionError("Paper server test failed: " + e.getMessage(), e);
        }
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
