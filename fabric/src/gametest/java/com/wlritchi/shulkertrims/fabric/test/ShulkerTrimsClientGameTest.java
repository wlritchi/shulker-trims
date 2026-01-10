package com.wlritchi.shulkertrims.fabric.test;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Client-side game tests for Shulker Trims mod.
 * Tests verify that trim overlays render correctly on both placed shulker boxes and items.
 *
 * <p>These tests use the Fabric Client Game Test API to:
 * <ul>
 *   <li>Create singleplayer worlds</li>
 *   <li>Place trimmed shulker boxes</li>
 *   <li>Take screenshots and compare against golden template images</li>
 * </ul>
 *
 * <p><b>Golden Image Workflow:</b>
 * <ul>
 *   <li>Template images are stored in {@code fabric/src/gametest/resources/templates/}</li>
 *   <li>If a template doesn't exist, the test will save the captured screenshot as the new template</li>
 *   <li>To update templates: delete the existing template and re-run the test</li>
 *   <li>Uses Mean Squared Difference algorithm with 1% tolerance for GPU/driver variations</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class ShulkerTrimsClientGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShulkerTrimsClientTest");

    // Legacy test trim configuration (for existing tests)
    private static final String WILD_PATTERN = "minecraft:wild";
    private static final String COPPER_MATERIAL = "minecraft:copper";
    private static final String SENTRY_PATTERN = "minecraft:sentry";
    private static final String GOLD_MATERIAL = "minecraft:gold";

    // All 18 trim patterns
    private static final List<String> ALL_PATTERNS = List.of(
            "minecraft:sentry", "minecraft:vex", "minecraft:wild", "minecraft:coast",
            "minecraft:dune", "minecraft:wayfinder", "minecraft:raiser", "minecraft:shaper",
            "minecraft:host", "minecraft:ward", "minecraft:silence", "minecraft:tide",
            "minecraft:snout", "minecraft:rib", "minecraft:eye", "minecraft:spire",
            "minecraft:flow", "minecraft:bolt"
    );

    // All 17 shulker box colors as blocks (undyed + 16 dye colors)
    private static final List<Block> ALL_SHULKER_BLOCKS = List.of(
            Blocks.SHULKER_BOX,           // undyed (purple-ish)
            Blocks.WHITE_SHULKER_BOX,
            Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX,
            Blocks.LIGHT_BLUE_SHULKER_BOX,
            Blocks.YELLOW_SHULKER_BOX,
            Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX,
            Blocks.GRAY_SHULKER_BOX,
            Blocks.LIGHT_GRAY_SHULKER_BOX,
            Blocks.CYAN_SHULKER_BOX,
            Blocks.PURPLE_SHULKER_BOX,
            Blocks.BLUE_SHULKER_BOX,
            Blocks.BROWN_SHULKER_BOX,
            Blocks.GREEN_SHULKER_BOX,
            Blocks.RED_SHULKER_BOX,
            Blocks.BLACK_SHULKER_BOX
    );

    // All 17 shulker box colors as items (for inventory tests)
    private static final List<net.minecraft.item.Item> ALL_SHULKER_ITEMS = List.of(
            Items.SHULKER_BOX,
            Items.WHITE_SHULKER_BOX,
            Items.ORANGE_SHULKER_BOX,
            Items.MAGENTA_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX,
            Items.YELLOW_SHULKER_BOX,
            Items.LIME_SHULKER_BOX,
            Items.PINK_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX,
            Items.LIGHT_GRAY_SHULKER_BOX,
            Items.CYAN_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX,
            Items.BROWN_SHULKER_BOX,
            Items.GREEN_SHULKER_BOX,
            Items.RED_SHULKER_BOX,
            Items.BLACK_SHULKER_BOX
    );

    // All 10 trim materials
    private static final List<String> ALL_MATERIALS = List.of(
            "minecraft:quartz", "minecraft:iron", "minecraft:netherite", "minecraft:redstone",
            "minecraft:copper", "minecraft:gold", "minecraft:emerald", "minecraft:diamond",
            "minecraft:lapis", "minecraft:amethyst"
    );

    /**
     * Material assignments for each shulker color index, chosen for good contrast.
     * Avoids same-hue combinations like white+iron, lime+emerald, cyan+diamond, etc.
     */
    private static final List<String> CONTRASTING_MATERIALS = List.of(
            "minecraft:quartz",     // 0: Undyed (purple) - white on purple = visible
            "minecraft:netherite",  // 1: White - dark on white = very visible (was iron - BAD)
            "minecraft:lapis",      // 2: Orange - blue on orange = visible
            "minecraft:gold",       // 3: Magenta - yellow on magenta = visible
            "minecraft:redstone",   // 4: Light Blue - red on light blue = visible
            "minecraft:amethyst",   // 5: Yellow - purple on yellow = visible
            "minecraft:copper",     // 6: Lime - brown on lime = visible (was emerald - BAD)
            "minecraft:emerald",    // 7: Pink - green on pink = visible
            "minecraft:diamond",    // 8: Gray - cyan on gray = visible
            "minecraft:redstone",   // 9: Light Gray - red on light gray = visible
            "minecraft:netherite",  // 10: Cyan - dark on cyan = visible (avoids diamond)
            "minecraft:gold",       // 11: Purple - yellow on purple = visible
            "minecraft:copper",     // 12: Blue - brown on blue = visible
            "minecraft:diamond",    // 13: Brown - cyan on brown = visible
            "minecraft:amethyst",   // 14: Green - purple on green = visible
            "minecraft:quartz",     // 15: Red - white on red = visible
            "minecraft:emerald",    // 16: Black - green on black = visible
            "minecraft:iron"        // 17: Undyed (repeat) - silver on purple = visible
    );

    /**
     * Mean Squared Difference threshold for screenshot comparison.
     * 0.01 (1%) provides tolerance for minor GPU/driver rendering differences
     * while still catching significant visual regressions.
     * Default Fabric threshold is 0.005 (0.5%), but CI environments with
     * XVFB may have slightly more variation.
     */
    private static final float COMPARISON_THRESHOLD = 0.01f;

    @Override
    public void runTest(ClientGameTestContext context) {
        LOGGER.info("Starting Shulker Trims client game tests");

        // Test 1: Comprehensive world rendering - all patterns, colors, and materials
        testComprehensiveWorldRendering(context);

        // Test 2: Comprehensive inventory rendering - all patterns, colors, and materials
        testComprehensiveInventoryRendering(context);

        LOGGER.info("Shulker Trims client game tests completed successfully");
    }

    /**
     * Comprehensive test of all trim patterns, shulker colors, and materials.
     * Places 18 shulker boxes in a 6x3 grid, each with a unique pattern.
     * Uses contrasting material assignments for visibility on each shulker color.
     */
    private void testComprehensiveWorldRendering(ClientGameTestContext context) {
        LOGGER.info("Test: Comprehensive world rendering - all patterns, colors, materials");

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {

            singleplayer.getClientWorld().waitForChunksRender();

            // Grid layout: 6 columns x 3 rows = 18 positions
            // 2 blocks spacing between shulkers (positions at x=0,3,6,9,12,15 and z=0,3,6)
            final int GRID_COLS = 6;
            final int GRID_ROWS = 3;
            final int SPACING = 3; // 1 block shulker + 2 empty = 3 apart
            final int BASE_Y = 100;

            // Calculate grid extents for camera positioning
            final int gridMaxX = (GRID_COLS - 1) * SPACING; // 15
            final int gridMaxZ = (GRID_ROWS - 1) * SPACING; // 6
            final int centerX = gridMaxX / 2; // ~7
            final int centerZ = gridMaxZ / 2; // ~3

            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();

                // Place all 18 shulkers with different patterns, colors, and contrasting materials
                int shulkerIndex = 0;
                for (int row = 0; row < GRID_ROWS; row++) {
                    for (int col = 0; col < GRID_COLS; col++) {
                        int x = col * SPACING;
                        int z = row * SPACING;
                        BlockPos pos = new BlockPos(x, BASE_Y, z);

                        // Each shulker gets a unique pattern (18 patterns for 18 positions)
                        String pattern = ALL_PATTERNS.get(shulkerIndex);

                        // Distribute colors: cycle through 17 colors (one will repeat for 18th)
                        Block shulkerBlock = ALL_SHULKER_BLOCKS.get(shulkerIndex % ALL_SHULKER_BLOCKS.size());

                        // Use contrasting material for this color (manually assigned for visibility)
                        String material = CONTRASTING_MATERIALS.get(shulkerIndex);

                        // Place the colored shulker block
                        world.setBlockState(pos, shulkerBlock.getDefaultState());

                        // Apply trim
                        if (world.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity be) {
                            if (be instanceof TrimmedShulkerBox trimmedBE) {
                                trimmedBE.shulkerTrims$setTrim(new ShulkerTrim(pattern, material));
                                be.markDirty();
                                LOGGER.info("Grid[{},{}]: {} with {} + {}",
                                        col, row, shulkerBlock.getName().getString(), pattern, material);
                            }
                        }

                        shulkerIndex++;
                    }
                }

                // Place a barrier block for the player to stand on (ensures consistent camera height)
                // Position in front-right of the grid (positive X, positive Z), looking back at grid
                // Shifted slightly left (-X, +Z) to better center the grid in frame
                int platformX = gridMaxX + 1;
                int platformY = BASE_Y + 3;
                int platformZ = gridMaxZ + 3;
                BlockPos playerPlatform = new BlockPos(platformX, platformY, platformZ);
                world.setBlockState(playerPlatform, Blocks.BARRIER.getDefaultState());
            });

            // Wait for updates and render
            context.waitTicks(20);
            singleplayer.getClientWorld().waitForChunksRender();

            // Set time to noon for consistent lighting (sun overhead, not in frame)
            singleplayer.getServer().runCommand("time set noon");
            context.waitTicks(5);

            // Position camera in front-right of grid, looking at ~45 degree angle
            // Player is at positive X, positive Z, looking back toward negative X, negative Z (northwest)
            // In Minecraft: yaw 0 = south (+Z), yaw 90 = west (-X), yaw 180 = north (-Z), yaw 270 = east (+X)
            // To look northwest (toward -X and -Z), yaw should be around 135 (between west=90 and north=180)
            float yaw = 135; // Looking northwest toward the grid
            float pitch = 35; // Looking down at the grid (slightly steeper when closer)
            int cameraX = gridMaxX + 1;
            int cameraY = BASE_Y + 4; // Standing on platform at BASE_Y + 3
            int cameraZ = gridMaxZ + 3;

            singleplayer.getServer().runCommand(
                    String.format("tp @p %d %d %d %.1f %.1f", cameraX, cameraY, cameraZ, yaw, pitch)
            );
            context.waitTicks(10);

            // Hide HUD for clean screenshot (no hearts, hotbar, crosshair, hand)
            context.runOnClient(client -> {
                client.options.hudHidden = true;
            });
            context.waitTicks(2);

            // Compare screenshot against golden template (higher resolution for detail)
            assertScreenshotMatchesTemplateHighRes(context, "shulker-trim-comprehensive-world", 1920, 1080);
            LOGGER.info("Screenshot comparison passed: shulker-trim-comprehensive-world");

            // Restore HUD
            context.runOnClient(client -> {
                client.options.hudHidden = false;
            });
        }
    }

    /**
     * Comprehensive test of all trim patterns, shulker colors, and materials in a chest GUI.
     * Places a chest with 18 trimmed shulker items and opens it.
     * Uses the same pattern/color/material combinations as the world test.
     */
    private void testComprehensiveInventoryRendering(ClientGameTestContext context) {
        LOGGER.info("Test: Comprehensive inventory rendering - all patterns, colors, materials");

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {

            singleplayer.getClientWorld().waitForChunksRender();

            // Wait for recipe unlock toast to disappear (takes ~5 seconds)
            context.waitTicks(120);

            BlockPos chestPos = new BlockPos(0, 100, 0);

            // Place a chest and fill it with all 18 trimmed shulker boxes
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();

                // Place chest
                world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());

                // Fill chest with trimmed shulkers
                if (world.getBlockEntity(chestPos) instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
                    for (int i = 0; i < 18; i++) {
                        // Each shulker gets a unique pattern
                        String pattern = ALL_PATTERNS.get(i);

                        // Cycle through colors (17 colors, one repeats)
                        net.minecraft.item.Item shulkerItem = ALL_SHULKER_ITEMS.get(i % ALL_SHULKER_ITEMS.size());

                        // Use contrasting material for visibility
                        String material = CONTRASTING_MATERIALS.get(i);

                        ItemStack stack = new ItemStack(shulkerItem);
                        ShulkerTrimStorage.writeTrimToItem(stack, new ShulkerTrim(pattern, material));

                        chest.setStack(i, stack);
                        LOGGER.info("Chest[{}]: {} with {} + {}",
                                i, shulkerItem.getName().getString(), pattern, material);
                    }
                }
            });

            context.waitTicks(10);

            // Teleport player near the chest and open it
            singleplayer.getServer().runCommand(
                    String.format("tp @p %d %d %d", chestPos.getX(), chestPos.getY(), chestPos.getZ() + 1)
            );
            context.waitTicks(5);

            // Hide HUD for clean screenshot (no hearts, hotbar, hand)
            context.runOnClient(client -> {
                client.options.hudHidden = true;
            });

            // Open the chest by simulating right-click
            context.runOnClient(client -> {
                if (client.player != null && client.interactionManager != null) {
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
            context.waitTicks(10);

            // Compare screenshot against golden template
            assertScreenshotMatchesTemplate(context, "shulker-trim-comprehensive-inventory");
            LOGGER.info("Screenshot comparison passed: shulker-trim-comprehensive-inventory");

            // Restore HUD and close chest
            context.runOnClient(client -> {
                client.options.hudHidden = false;
                client.setScreen(null);
            });
            context.waitForScreen(null);
        }
    }

    /**
     * Asserts that a screenshot matches the golden template image.
     *
     * <p>Uses Mean Squared Difference algorithm with configurable tolerance to handle
     * minor GPU/driver rendering variations while still catching significant visual regressions.
     *
     * <p>If the template image doesn't exist, the screenshot will be saved as the new template.
     * This enables easy initial template generation by simply running the tests.
     *
     * @param context the client game test context
     * @param templateName the name of the template image (without extension)
     */
    private void assertScreenshotMatchesTemplate(ClientGameTestContext context, String templateName) {
        // Note: Fabric API automatically adds .png extension to the template name
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of(templateName)
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .save(); // Also save the actual screenshot for debugging comparison failures
        context.assertScreenshotEquals(options);
    }

    /**
     * Asserts that a screenshot matches the golden template image at a specified resolution.
     * Higher resolution captures more detail for visual inspection.
     *
     * @param context the client game test context
     * @param templateName the name of the template image (without extension)
     * @param width screenshot width in pixels
     * @param height screenshot height in pixels
     */
    private void assertScreenshotMatchesTemplateHighRes(ClientGameTestContext context, String templateName, int width, int height) {
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of(templateName)
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .withSize(width, height)
                .save();
        context.assertScreenshotEquals(options);
    }

    /**
     * Helper method to place a shulker box and apply a trim.
     */
    private void placeAndTrimShulker(ServerWorld world, BlockPos pos, String pattern, String material) {
        world.setBlockState(pos, Blocks.SHULKER_BOX.getDefaultState());

        if (world.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity be) {
            if (be instanceof TrimmedShulkerBox trimmedBE) {
                trimmedBE.shulkerTrims$setTrim(new ShulkerTrim(pattern, material));
                be.markDirty();
                LOGGER.info("Placed trimmed shulker at {}: pattern={}, material={}", pos, pattern, material);
            }
        }
    }
}
