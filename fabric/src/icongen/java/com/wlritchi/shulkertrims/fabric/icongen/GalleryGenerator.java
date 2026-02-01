package com.wlritchi.shulkertrims.fabric.icongen;

import com.dimaskama.orthocamera.client.OrthoCamera;
import com.wlritchi.shulkertrims.common.ShulkerTrim;
import com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a gallery/demo image showing multiple trimmed shulker boxes in a storage room setting.
 */
@SuppressWarnings("UnstableApiUsage")
public class GalleryGenerator implements FabricClientGameTest {

  private static final Logger LOGGER = LoggerFactory.getLogger("ShulkerTrimsGallery");

  /** Shulker box configurations: color, pattern, material, position, facing. */
  private record ShulkerPlacement(
      Block block, String pattern, String material, BlockPos pos, Direction facing) {}

  /** Room origin - all positions are relative to this. */
  private static final BlockPos ROOM_ORIGIN = new BlockPos(0, 100, 0);

  /** The 6 curated shulker boxes for the gallery. */
  private static final List<ShulkerPlacement> SHULKERS =
      List.of(
          new ShulkerPlacement(
              Blocks.PURPLE_SHULKER_BOX, "sentry", "gold", ROOM_ORIGIN.add(1, 1, 3), Direction.UP),
          new ShulkerPlacement(
              Blocks.CYAN_SHULKER_BOX, "wild", "copper", ROOM_ORIGIN.add(3, 1, 3), Direction.UP),
          new ShulkerPlacement(
              Blocks.WHITE_SHULKER_BOX,
              "dune",
              "netherite",
              ROOM_ORIGIN.add(0, 1, 1),
              Direction.UP),
          new ShulkerPlacement(
              Blocks.ORANGE_SHULKER_BOX,
              "coast",
              "diamond",
              ROOM_ORIGIN.add(2, 1, 1),
              Direction.SOUTH),
          new ShulkerPlacement(
              Blocks.LIGHT_GRAY_SHULKER_BOX,
              "vex",
              "amethyst",
              ROOM_ORIGIN.add(4, 1, 2),
              Direction.UP),
          new ShulkerPlacement(
              Blocks.BLACK_SHULKER_BOX, "rib", "gold", ROOM_ORIGIN.add(1, 1, 2), Direction.UP));

  @Override
  public void runTest(ClientGameTestContext context) {
    // Disable OrthoCamera - we want regular first-person perspective
    OrthoCamera.CONFIG.enabled = false;

    GalleryConfig config = new GalleryConfig();
    LOGGER.info("Starting gallery generation");
    LOGGER.info("Output: {}", config.getOutputPath());
    LOGGER.info("Size: {}x{}", config.getWidth(), config.getHeight());

    try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
      singleplayer.getClientWorld().waitForChunksRender();

      buildRoom(singleplayer);
      placeShulkers(singleplayer);
      configureWorld(singleplayer, context);
      positionCamera(singleplayer, context);
      captureScreenshot(context, config);
    }

    LOGGER.info("Gallery generation complete");
  }

  /** Builds the storage room structure. */
  private void buildRoom(TestSingleplayerContext singleplayer) {
    singleplayer
        .getServer()
        .runOnServer(
            server -> {
              ServerWorld world = server.getOverworld();

              // Room dimensions: 5 wide (x), 4 deep (z), 3 tall (y)
              int width = 5;
              int depth = 4;
              int height = 3;

              BlockPos origin = ROOM_ORIGIN;

              // Floor - oak planks
              for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                  world.setBlockState(origin.add(x, 0, z), Blocks.OAK_PLANKS.getDefaultState());
                }
              }

              // Back wall (z = depth-1) - spruce planks with stripped log accents
              for (int x = 0; x < width; x++) {
                for (int y = 1; y <= height; y++) {
                  Block block = (x == 2) ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_PLANKS;
                  world.setBlockState(origin.add(x, y, depth - 1), block.getDefaultState());
                }
              }

              // Side walls - partial for framing
              for (int z = 0; z < depth - 1; z++) {
                for (int y = 1; y <= height; y++) {
                  // Left wall (x = 0)
                  world.setBlockState(origin.add(-1, y, z), Blocks.SPRUCE_PLANKS.getDefaultState());
                  // Right wall (x = width)
                  world.setBlockState(
                      origin.add(width, y, z), Blocks.SPRUCE_PLANKS.getDefaultState());
                }
              }

              // Shelves - spruce slabs at y+2 along back wall
              for (int x = 0; x < width; x++) {
                world.setBlockState(
                    origin.add(x, 2, depth - 2), Blocks.SPRUCE_SLAB.getDefaultState());
              }

              // Lanterns - warm lighting
              world.setBlockState(origin.add(1, 3, 1), Blocks.LANTERN.getDefaultState());
              world.setBlockState(origin.add(4, 3, 2), Blocks.LANTERN.getDefaultState());

              // Props - barrel and chest
              world.setBlockState(origin.add(4, 1, 0), Blocks.BARREL.getDefaultState());
              world.setBlockState(origin.add(0, 1, 0), Blocks.CHEST.getDefaultState());

              LOGGER.info("Room built: {}x{}x{} at {}", width, depth, height, origin);
            });
  }

  /** Places the 6 trimmed shulker boxes. */
  private void placeShulkers(TestSingleplayerContext singleplayer) {
    singleplayer
        .getServer()
        .runOnServer(
            server -> {
              ServerWorld world = server.getOverworld();

              for (ShulkerPlacement placement : SHULKERS) {
                // Get block state with facing direction
                BlockState state =
                    placement
                        .block
                        .getDefaultState()
                        .with(ShulkerBoxBlock.FACING, placement.facing);
                world.setBlockState(placement.pos, state);

                // Apply trim
                if (world.getBlockEntity(placement.pos) instanceof ShulkerBoxBlockEntity be) {
                  if (be instanceof TrimmedShulkerBox trimmedBE) {
                    String fullPattern = "minecraft:" + placement.pattern;
                    String fullMaterial = "minecraft:" + placement.material;
                    trimmedBE.shulkerTrims$setTrim(new ShulkerTrim(fullPattern, fullMaterial));
                    be.markDirty();
                  }
                }
              }
              LOGGER.info("Placed {} shulker boxes", SHULKERS.size());
            });
  }

  /** Configures world settings for consistent rendering. */
  private void configureWorld(TestSingleplayerContext singleplayer, ClientGameTestContext context) {
    singleplayer.getServer().runCommand("time set noon");
    singleplayer.getServer().runCommand("gamerule doDaylightCycle false");
    singleplayer.getServer().runCommand("gamemode spectator @p");
    context.waitTicks(10);
  }

  /** Positions the camera for the gallery shot. */
  private void positionCamera(TestSingleplayerContext singleplayer, ClientGameTestContext context) {
    // TODO: Fine-tune camera position
    // Position looking at the room from slightly elevated angle
    singleplayer.getServer().runCommand("tp @p 2 102 -2 0 20");
    context.waitTicks(10);

    // Hide HUD
    context.runOnClient(
        client -> {
          client.options.hudHidden = true;
        });
    context.waitTicks(5);
  }

  /** Captures the screenshot and saves to output path. */
  private void captureScreenshot(ClientGameTestContext context, GalleryConfig config) {
    context.runOnClient(
        client -> {
          try {
            Files.createDirectories(config.getOutputPath().getParent());
            client.getWindow().setWindowedSize(config.getWidth(), config.getHeight());
          } catch (IOException e) {
            LOGGER.error("Failed to create output directory", e);
          }
        });

    context.waitTicks(5);

    context.runOnClient(
        client -> {
          Framebuffer framebuffer = client.getFramebuffer();
          ScreenshotRecorder.takeScreenshot(
              framebuffer,
              image -> {
                try {
                  image.writeTo(config.getOutputPath());
                  LOGGER.info("Saved gallery image: {}", config.getOutputPath());
                } catch (IOException e) {
                  LOGGER.error("Failed to save gallery image", e);
                } finally {
                  image.close();
                }
              });
        });

    context.waitTicks(2);
  }
}
