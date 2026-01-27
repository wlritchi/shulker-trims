package com.wlritchi.shulkertrims.fabric.icongen;

import com.dimaskama.orthocamera.client.OrthoCamera;
import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates isometric PNG renders of trimmed shulker boxes.
 * Uses OrthoCamera for orthographic projection and Fabric's game test API for screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class IconGenerator implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShulkerTrimsIconGen");

    // Shulker color name to block mapping
    private static final Map<String, Block> COLOR_TO_BLOCK = Map.ofEntries(
            Map.entry("default", Blocks.SHULKER_BOX),
            Map.entry("white", Blocks.WHITE_SHULKER_BOX),
            Map.entry("orange", Blocks.ORANGE_SHULKER_BOX),
            Map.entry("magenta", Blocks.MAGENTA_SHULKER_BOX),
            Map.entry("light_blue", Blocks.LIGHT_BLUE_SHULKER_BOX),
            Map.entry("yellow", Blocks.YELLOW_SHULKER_BOX),
            Map.entry("lime", Blocks.LIME_SHULKER_BOX),
            Map.entry("pink", Blocks.PINK_SHULKER_BOX),
            Map.entry("gray", Blocks.GRAY_SHULKER_BOX),
            Map.entry("light_gray", Blocks.LIGHT_GRAY_SHULKER_BOX),
            Map.entry("cyan", Blocks.CYAN_SHULKER_BOX),
            Map.entry("purple", Blocks.PURPLE_SHULKER_BOX),
            Map.entry("blue", Blocks.BLUE_SHULKER_BOX),
            Map.entry("brown", Blocks.BROWN_SHULKER_BOX),
            Map.entry("green", Blocks.GREEN_SHULKER_BOX),
            Map.entry("red", Blocks.RED_SHULKER_BOX),
            Map.entry("black", Blocks.BLACK_SHULKER_BOX)
    );

    @Override
    public void runTest(ClientGameTestContext context) {
        IconGenConfig config = new IconGenConfig();

        LOGGER.info("Starting icon generation");
        LOGGER.info("Colors: {}", config.getColors());
        LOGGER.info("Patterns: {}", config.getPatterns());
        LOGGER.info("Materials: {}", config.getMaterials());
        LOGGER.info("Output: {}", config.getOutputDir());
        LOGGER.info("Size: {}x{}", config.getSize(), config.getSize());
        LOGGER.info("Total combinations: {}", config.getTotalCombinations());

        int generated = 0;

        for (String color : config.getColors()) {
            for (String pattern : config.getPatterns()) {
                for (String material : config.getMaterials()) {
                    LOGGER.info("Generating: color={}, pattern={}, material={}", color, pattern, material);

                    try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
                        singleplayer.getClientWorld().waitForChunksRender();

                        setupScene(singleplayer, context, color, pattern, material);
                        captureIcon(context, config, color, pattern, material);

                        generated++;
                    }

                    // Disable OrthoCamera between generations to reset state
                    OrthoCamera.CONFIG.enabled = false;
                }
            }
        }

        LOGGER.info("Icon generation complete: {} icons generated", generated);
    }

    /**
     * Sets up the scene for icon capture: creates world, places shulker, configures camera.
     */
    private void setupScene(TestSingleplayerContext singleplayer, ClientGameTestContext context,
                           String color, String pattern, String material) {
        BlockPos shulkerPos = new BlockPos(0, 100, 0);

        // Place the shulker box with trim
        singleplayer.getServer().runOnServer(server -> {
            ServerWorld world = server.getOverworld();

            Block shulkerBlock = COLOR_TO_BLOCK.getOrDefault(color, Blocks.PURPLE_SHULKER_BOX);
            world.setBlockState(shulkerPos, shulkerBlock.getDefaultState());

            if (world.getBlockEntity(shulkerPos) instanceof ShulkerBoxBlockEntity be) {
                if (be instanceof TrimmedShulkerBox trimmedBE) {
                    String fullPattern = pattern.contains(":") ? pattern : "minecraft:" + pattern;
                    String fullMaterial = material.contains(":") ? material : "minecraft:" + material;
                    trimmedBE.shulkerTrims$setTrim(new ShulkerTrim(fullPattern, fullMaterial));
                    be.markDirty();
                }
            }
        });

        // Wait for chunk to render
        context.waitTicks(20);
        singleplayer.getClientWorld().waitForChunksRender();

        // Set time to noon for consistent lighting
        singleplayer.getServer().runCommand("time set noon");
        singleplayer.getServer().runCommand("gamerule doDaylightCycle false");
        context.waitTicks(5);

        // Put player in spectator mode to prevent falling
        singleplayer.getServer().runCommand("gamemode spectator @p");
        context.waitTicks(5);

        // Configure OrthoCamera for isometric view BEFORE positioning camera
        configureOrthoCamera();

        // Position player at the shulker, with isometric rotation
        // In OrthoCamera non-fixed mode, the player's rotation determines view direction
        // Yaw 225 (looking southwest), pitch 35 (steeper downward)
        // Fine-tuned position for centered framing
        singleplayer.getServer().runCommand("tp @p 0.5 100.3 0.5 225 35");
        context.waitTicks(10);

        // Hide HUD for clean capture
        context.runOnClient(client -> {
            client.options.hudHidden = true;
        });
        context.waitTicks(2);
    }

    /**
     * Configures OrthoCamera for isometric icon rendering.
     */
    private void configureOrthoCamera() {
        var config = OrthoCamera.CONFIG;
        config.enabled = true;
        config.fixed = false;  // Don't fix - use player's actual rotation
        // Scale determines zoom level in orthographic mode
        // Lower scale = more zoomed in
        // Scale 1.2 gives some margin around shulker for centering
        config.setScaleX(1.2f);
        config.setScaleY(1.2f);
        config.auto_third_person = false;
    }

    /**
     * Captures screenshot and saves to output directory with transparent background.
     */
    private void captureIcon(ClientGameTestContext context, IconGenConfig config,
                             String color, String pattern, String material) {
        String filename = String.format("shulker-%s-%s-%s.png", color, pattern, material);
        Path outputPath = config.getOutputDir().resolve(filename);

        context.runOnClient(client -> {
            try {
                // Ensure output directory exists
                Files.createDirectories(config.getOutputDir());

                // Resize window to target size for clean capture
                client.getWindow().setWindowedSize(config.getSize(), config.getSize());

                // Wait a frame for resize to take effect
            } catch (IOException e) {
                LOGGER.error("Failed to create output directory", e);
            }
        });

        context.waitTicks(5);

        // Use Minecraft's screenshot mechanism with consumer callback
        context.runOnClient(client -> {
            Framebuffer framebuffer = client.getFramebuffer();

            // Capture the framebuffer - newer API uses a consumer callback
            ScreenshotRecorder.takeScreenshot(framebuffer, nativeImage -> {
                try {
                    // Save to file
                    nativeImage.writeTo(outputPath);
                    LOGGER.info("Saved icon: {}", outputPath);
                } catch (IOException e) {
                    LOGGER.error("Failed to save icon: {}", outputPath, e);
                } finally {
                    nativeImage.close();
                }
            });
        });

        context.waitTicks(2);
    }
}
