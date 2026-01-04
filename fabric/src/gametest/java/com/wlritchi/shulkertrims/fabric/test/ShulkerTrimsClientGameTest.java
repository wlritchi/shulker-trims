package com.wlritchi.shulkertrims.fabric.test;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.ShulkerTrimStorage;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
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

    // Test trim configuration
    private static final String WILD_PATTERN = "minecraft:wild";
    private static final String COPPER_MATERIAL = "minecraft:copper";
    private static final String SENTRY_PATTERN = "minecraft:sentry";
    private static final String GOLD_MATERIAL = "minecraft:gold";

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

        // Test 1: World rendering of trimmed shulker box block
        testWorldRenderingTrimmedShulker(context);

        // Test 2: Inventory rendering of trimmed shulker box item
        testInventoryRenderingTrimmedShulker(context);

        // Test 3: Multiple trim patterns (for visual comparison)
        testMultipleTrimPatterns(context);

        LOGGER.info("Shulker Trims client game tests completed successfully");
    }

    /**
     * Test that a trimmed shulker box renders correctly when placed in the world.
     * Creates a world, builds a platform, places a trimmed shulker, waits for render, and takes a screenshot.
     */
    private void testWorldRenderingTrimmedShulker(ClientGameTestContext context) {
        LOGGER.info("Test: World rendering of trimmed shulker box");

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {

            // Wait for chunks to be fully rendered
            singleplayer.getClientWorld().waitForChunksRender();
            LOGGER.info("World loaded and chunks rendered");

            // Build a small platform and place a trimmed shulker box
            // Use a high Y coordinate to be above terrain
            BlockPos platformPos = new BlockPos(0, 100, 0);
            BlockPos shulkerPos = platformPos.up();

            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();

                // Build a small stone platform for visibility
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        world.setBlockState(platformPos.add(x, 0, z), Blocks.STONE.getDefaultState());
                    }
                }

                // Place shulker on the platform
                world.setBlockState(shulkerPos, Blocks.SHULKER_BOX.getDefaultState());

                // Get block entity and apply trim
                if (world.getBlockEntity(shulkerPos) instanceof ShulkerBoxBlockEntity be) {
                    if (be instanceof TrimmedShulkerBox trimmedBE) {
                        ShulkerTrim trim = new ShulkerTrim(WILD_PATTERN, COPPER_MATERIAL);
                        trimmedBE.shulkerTrims$setTrim(trim);
                        be.markDirty();
                        LOGGER.info("Applied trim to shulker at {}: pattern={}, material={}",
                                shulkerPos, WILD_PATTERN, COPPER_MATERIAL);
                    }
                }
            });

            // Wait for the block update to sync to client and re-render
            context.waitTicks(20);
            singleplayer.getClientWorld().waitForChunksRender();

            // Position camera to view the shulker
            singleplayer.getServer().runCommand(
                    String.format("tp @p %d %d %d 0 30", shulkerPos.getX(), shulkerPos.getY() + 2, shulkerPos.getZ() - 3)
            );
            context.waitTicks(10);

            // Compare screenshot against golden template image
            // If template doesn't exist, it will be created automatically
            assertScreenshotMatchesTemplate(context, "shulker-trim-world-wild-copper");
            LOGGER.info("Screenshot comparison passed: shulker-trim-world-wild-copper");
        }
    }

    /**
     * Test that a trimmed shulker box item renders correctly in the player's inventory.
     * Opens the inventory screen and takes a screenshot of the trimmed shulker in a hotbar slot.
     */
    private void testInventoryRenderingTrimmedShulker(ClientGameTestContext context) {
        LOGGER.info("Test: Inventory rendering of trimmed shulker box");

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {

            singleplayer.getClientWorld().waitForChunksRender();

            // Give player a trimmed shulker box
            singleplayer.getServer().runOnServer(server -> {
                server.getPlayerManager().getPlayerList().forEach(player -> {
                    ItemStack trimmedShulker = new ItemStack(Items.SHULKER_BOX);
                    ShulkerTrim trim = new ShulkerTrim(WILD_PATTERN, COPPER_MATERIAL);
                    ShulkerTrimStorage.writeTrimToItem(trimmedShulker, trim);

                    // Put in first hotbar slot
                    player.getInventory().setStack(0, trimmedShulker);
                    LOGGER.info("Gave player trimmed shulker in hotbar slot 0");
                });
            });

            context.waitTicks(10);

            // Open inventory screen
            context.runOnClient(client -> {
                if (client.player != null) {
                    client.setScreen(new InventoryScreen(client.player));
                }
            });

            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(5); // Allow screen to fully render

            // Compare screenshot against golden template image
            assertScreenshotMatchesTemplate(context, "shulker-trim-inventory-wild-copper");
            LOGGER.info("Screenshot comparison passed: shulker-trim-inventory-wild-copper");

            // Close inventory
            context.runOnClient(client -> client.setScreen(null));
            context.waitForScreen(null);
        }
    }

    /**
     * Test multiple trim patterns to provide variety for visual comparison.
     * Places several differently-trimmed shulkers in a row.
     */
    private void testMultipleTrimPatterns(ClientGameTestContext context) {
        LOGGER.info("Test: Multiple trim patterns comparison");

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {

            singleplayer.getClientWorld().waitForChunksRender();

            // Place multiple shulkers with different trims on a platform high in the sky
            BlockPos basePos = new BlockPos(0, 100, 5);

            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();

                // Build a platform under the shulkers
                BlockPos platformBase = basePos.down();
                for (int x = -1; x <= 7; x++) {
                    for (int z = -1; z <= 1; z++) {
                        world.setBlockState(platformBase.add(x, 0, z), Blocks.STONE.getDefaultState());
                    }
                }

                // Shulker 1: Wild + Copper
                placeAndTrimShulker(world, basePos, WILD_PATTERN, COPPER_MATERIAL);

                // Shulker 2: Sentry + Gold
                placeAndTrimShulker(world, basePos.east(2), SENTRY_PATTERN, GOLD_MATERIAL);

                // Shulker 3: Wild + Gold (different material)
                placeAndTrimShulker(world, basePos.east(4), WILD_PATTERN, GOLD_MATERIAL);

                // Shulker 4: Plain shulker (no trim) for comparison
                world.setBlockState(basePos.east(6), Blocks.SHULKER_BOX.getDefaultState());
                LOGGER.info("Placed plain shulker for comparison at {}", basePos.east(6));
            });

            // Wait for updates and render
            context.waitTicks(20);
            singleplayer.getClientWorld().waitForChunksRender();

            // Position camera to view all shulkers
            singleplayer.getServer().runCommand(
                    String.format("tp @p %d %d %d 0 45", basePos.getX() + 3, basePos.getY() + 4, basePos.getZ() - 5)
            );
            context.waitTicks(10);

            // Compare screenshot against golden template image
            assertScreenshotMatchesTemplate(context, "shulker-trim-multiple-patterns");
            LOGGER.info("Screenshot comparison passed: shulker-trim-multiple-patterns");

            // Also give player multiple trimmed shulkers for inventory comparison
            singleplayer.getServer().runOnServer(server -> {
                server.getPlayerManager().getPlayerList().forEach(player -> {
                    PlayerInventory inv = player.getInventory();

                    // Slot 0: Wild + Copper
                    ItemStack s1 = new ItemStack(Items.SHULKER_BOX);
                    ShulkerTrimStorage.writeTrimToItem(s1, new ShulkerTrim(WILD_PATTERN, COPPER_MATERIAL));
                    inv.setStack(0, s1);

                    // Slot 1: Sentry + Gold
                    ItemStack s2 = new ItemStack(Items.SHULKER_BOX);
                    ShulkerTrimStorage.writeTrimToItem(s2, new ShulkerTrim(SENTRY_PATTERN, GOLD_MATERIAL));
                    inv.setStack(1, s2);

                    // Slot 2: Blue shulker + Wild + Copper
                    ItemStack s3 = new ItemStack(Items.BLUE_SHULKER_BOX);
                    ShulkerTrimStorage.writeTrimToItem(s3, new ShulkerTrim(WILD_PATTERN, COPPER_MATERIAL));
                    inv.setStack(2, s3);

                    // Slot 3: Plain shulker (no trim)
                    inv.setStack(3, new ItemStack(Items.SHULKER_BOX));

                    LOGGER.info("Gave player multiple trimmed shulkers for inventory test");
                });
            });

            context.waitTicks(10);

            // Open inventory for item comparison
            context.runOnClient(client -> {
                if (client.player != null) {
                    client.setScreen(new InventoryScreen(client.player));
                }
            });

            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(5);

            // Compare screenshot against golden template image
            assertScreenshotMatchesTemplate(context, "shulker-trim-inventory-multiple");
            LOGGER.info("Screenshot comparison passed: shulker-trim-inventory-multiple");

            context.runOnClient(client -> client.setScreen(null));
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
        TestScreenshotComparisonOptions options = TestScreenshotComparisonOptions.of(templateName + ".png")
                .withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(COMPARISON_THRESHOLD))
                .save(); // Also save the actual screenshot for debugging comparison failures
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
