# Gallery Image Generator Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Generate a promotional gallery image showing 6 trimmed shulker boxes in a cozy storage room.

**Architecture:** New `GalleryGenerator` class in icongen source set, builds room programmatically, places shulkers with trims, captures screenshot from first-person perspective.

**Tech Stack:** Fabric Client GameTest API, existing icongen infrastructure, vanilla Minecraft blocks.

---

### Task 1: Create GalleryConfig

**Files:**
- Create: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryConfig.java`

**Step 1: Create the config class**

```java
package com.wlritchi.shulkertrims.fabric.icongen;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Configuration for gallery image generation. */
public class GalleryConfig {

  private static final String PREFIX = "shulker_trims.gallery.";

  private final Path outputPath;
  private final int width;
  private final int height;

  public GalleryConfig() {
    this.outputPath = Paths.get(System.getProperty(PREFIX + "output", "build/gallery/gallery.png"));
    this.width = Integer.parseInt(System.getProperty(PREFIX + "width", "1920"));
    this.height = Integer.parseInt(System.getProperty(PREFIX + "height", "1080"));
  }

  public Path getOutputPath() {
    return outputPath;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}
```

**Step 2: Format the file**

Run: `./gradlew :fabric:spotlessApply`

**Step 3: Verify compilation**

Run: `./gradlew :fabric:compileIcongenJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryConfig.java
git commit -m "feat(icongen): add GalleryConfig for gallery image generation"
```

---

### Task 2: Create GalleryGenerator skeleton

**Files:**
- Create: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryGenerator.java`

**Step 1: Create the generator skeleton**

This creates the basic structure with empty helper methods that we'll fill in subsequent tasks.

```java
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
              Blocks.PURPLE_SHULKER_BOX, "sentry", "gold",
              ROOM_ORIGIN.add(1, 1, 3), Direction.UP),
          new ShulkerPlacement(
              Blocks.CYAN_SHULKER_BOX, "wild", "copper",
              ROOM_ORIGIN.add(3, 1, 3), Direction.UP),
          new ShulkerPlacement(
              Blocks.WHITE_SHULKER_BOX, "dune", "netherite",
              ROOM_ORIGIN.add(0, 1, 1), Direction.UP),
          new ShulkerPlacement(
              Blocks.ORANGE_SHULKER_BOX, "coast", "diamond",
              ROOM_ORIGIN.add(2, 1, 1), Direction.SOUTH),
          new ShulkerPlacement(
              Blocks.LIGHT_GRAY_SHULKER_BOX, "vex", "amethyst",
              ROOM_ORIGIN.add(4, 1, 2), Direction.UP),
          new ShulkerPlacement(
              Blocks.BLACK_SHULKER_BOX, "rib", "gold",
              ROOM_ORIGIN.add(1, 1, 2), Direction.UP));

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
    singleplayer.getServer().runOnServer(server -> {
      ServerWorld world = server.getOverworld();
      // TODO: Build room structure
      LOGGER.info("Room built");
    });
  }

  /** Places the 6 trimmed shulker boxes. */
  private void placeShulkers(TestSingleplayerContext singleplayer) {
    singleplayer.getServer().runOnServer(server -> {
      ServerWorld world = server.getOverworld();

      for (ShulkerPlacement placement : SHULKERS) {
        // Get block state with facing direction
        BlockState state = placement.block.getDefaultState()
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
    context.runOnClient(client -> {
      client.options.hudHidden = true;
    });
    context.waitTicks(5);
  }

  /** Captures the screenshot and saves to output path. */
  private void captureScreenshot(ClientGameTestContext context, GalleryConfig config) {
    context.runOnClient(client -> {
      try {
        Files.createDirectories(config.getOutputPath().getParent());
        client.getWindow().setWindowedSize(config.getWidth(), config.getHeight());
      } catch (IOException e) {
        LOGGER.error("Failed to create output directory", e);
      }
    });

    context.waitTicks(5);

    context.runOnClient(client -> {
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
```

**Step 2: Format the file**

Run: `./gradlew :fabric:spotlessApply`

**Step 3: Verify compilation**

Run: `./gradlew :fabric:compileIcongenJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryGenerator.java
git commit -m "feat(icongen): add GalleryGenerator skeleton"
```

---

### Task 3: Add Gradle task for gallery generation

**Files:**
- Modify: `fabric/build.gradle.kts` (add after the icongen run configuration, around line 288)

**Step 1: Add gallery run configuration and task**

Add after the existing `runs { register("icongen") { ... } }` block (inside the `loom { }` block):

```kotlin
        register("gallery") {
            client()
            name = "Generate Gallery"
            source(sourceSets.getByName("icongen"))
            vmArg("-Dfabric.client.gametest")
            vmArg("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
            // Only run GalleryGenerator, not IconGenerator
            vmArg("-Dfabric.client.gametest.testClass=com.wlritchi.shulkertrims.fabric.icongen.GalleryGenerator")
        }
```

Then add after the existing `afterEvaluate { tasks.named<JavaExec>("runIcongen") { ... } }` block:

```kotlin
    tasks.named<JavaExec>("runGallery") {
        // Pass through shulker_trims.gallery.* system properties
        System.getProperties().forEach { key, value ->
            if (key.toString().startsWith("shulker_trims.gallery.")) {
                jvmArgs("-D$key=$value")
            }
        }

        // Set default output directory
        val outputPath = project.layout.buildDirectory.file("gallery/gallery.png").get().asFile
        jvmArgs("-Dshulker_trims.gallery.output=${outputPath.absolutePath}")
    }
```

And add a convenience task after the existing `generateIcon` task:

```kotlin
tasks.register("generateGallery") {
    group = "shulker trims"
    description = "Generate gallery/demo image of trimmed shulker boxes"
    dependsOn("runGallery")
}
```

**Step 2: Format the file**

Run: `./gradlew :fabric:spotlessApply` (may not affect .kts files, that's fine)

**Step 3: Verify task exists**

Run: `./gradlew tasks --group="shulker trims"`
Expected: Should show both `generateIcon` and `generateGallery` tasks

**Step 4: Commit**

```bash
git add fabric/build.gradle.kts
git commit -m "build: add generateGallery task for gallery image generation"
```

---

### Task 4: Test initial generation and capture baseline

**Step 1: Run the gallery generator**

Run: `xvfb-run -a ./gradlew :fabric:generateGallery`
Expected: BUILD SUCCESSFUL, image saved to `fabric/build/gallery/gallery.png`

**Step 2: Visually inspect the output**

Use a subagent to assess the generated image:
- Are the shulker boxes visible?
- Is the scene rendering correctly?
- What needs adjustment?

**Step 3: Commit working baseline (if successful)**

```bash
git add -A
git commit -m "feat(icongen): working gallery generator baseline"
```

---

### Task 5: Build the storage room structure

**Files:**
- Modify: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryGenerator.java`

**Step 1: Implement buildRoom method**

Replace the `buildRoom` method with actual room construction:

```java
  /** Builds the storage room structure. */
  private void buildRoom(TestSingleplayerContext singleplayer) {
    singleplayer.getServer().runOnServer(server -> {
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
          world.setBlockState(origin.add(width, y, z), Blocks.SPRUCE_PLANKS.getDefaultState());
        }
      }

      // Shelves - spruce slabs at y+2 along back wall
      for (int x = 0; x < width; x++) {
        world.setBlockState(origin.add(x, 2, depth - 2), Blocks.SPRUCE_SLAB.getDefaultState());
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
```

**Step 2: Format and compile**

Run: `./gradlew :fabric:spotlessApply && ./gradlew :fabric:compileIcongenJava`

**Step 3: Test the room build**

Run: `xvfb-run -a ./gradlew :fabric:generateGallery`

**Step 4: Visually assess with subagent**

Check: Does the room look like a cozy storage area? Are proportions good?

**Step 5: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryGenerator.java
git commit -m "feat(icongen): build storage room structure in gallery"
```

---

### Task 6: Refine shulker placements for the room

**Files:**
- Modify: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryGenerator.java`

**Step 1: Update SHULKERS positions to fit room layout**

The positions need to work with the room structure. Update the `SHULKERS` list with positions that:
- Place some on the shelf (y = ROOM_ORIGIN.y + 3, z = depth - 2)
- Place some on the floor (y = ROOM_ORIGIN.y + 1)
- Vary facing directions

```java
  /** The 6 curated shulker boxes for the gallery. */
  private static final List<ShulkerPlacement> SHULKERS =
      List.of(
          // On shelf - facing down to show trim to camera
          new ShulkerPlacement(
              Blocks.PURPLE_SHULKER_BOX, "sentry", "gold",
              ROOM_ORIGIN.add(1, 3, 2), Direction.DOWN),
          new ShulkerPlacement(
              Blocks.CYAN_SHULKER_BOX, "wild", "copper",
              ROOM_ORIGIN.add(3, 3, 2), Direction.DOWN),
          // On floor - mix of orientations
          new ShulkerPlacement(
              Blocks.WHITE_SHULKER_BOX, "dune", "netherite",
              ROOM_ORIGIN.add(0, 1, 1), Direction.UP),
          new ShulkerPlacement(
              Blocks.ORANGE_SHULKER_BOX, "coast", "diamond",
              ROOM_ORIGIN.add(2, 1, 1), Direction.SOUTH),  // On side, facing camera
          new ShulkerPlacement(
              Blocks.LIGHT_GRAY_SHULKER_BOX, "vex", "amethyst",
              ROOM_ORIGIN.add(3, 1, 0), Direction.UP),
          new ShulkerPlacement(
              Blocks.BLACK_SHULKER_BOX, "rib", "gold",
              ROOM_ORIGIN.add(1, 1, 2), Direction.UP));
```

**Step 2: Format and test**

Run: `./gradlew :fabric:spotlessApply && xvfb-run -a ./gradlew :fabric:generateGallery`

**Step 3: Visual assessment with subagent**

Check: Are shulkers visible? Do trims show? Good composition?

**Step 4: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryGenerator.java
git commit -m "feat(icongen): refine shulker placements in gallery room"
```

---

### Task 7: Fine-tune camera position

**Files:**
- Modify: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryGenerator.java`

**Step 1: Iterate on camera position**

This will require visual iteration. The camera command format is:
`tp @p <x> <y> <z> <yaw> <pitch>`

- yaw: 0 = south, 90 = west, 180 = north, 270 = east
- pitch: negative = looking up, positive = looking down

Starting point for looking into room from front:
```java
singleplayer.getServer().runCommand("tp @p 2 102 -2 0 25");
```

Adjust based on visual feedback until composition looks good.

**Step 2: Test and iterate**

Run: `xvfb-run -a ./gradlew :fabric:generateGallery`

Use subagent to assess, adjust coordinates, repeat.

**Step 3: Commit final camera position**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/GalleryGenerator.java
git commit -m "feat(icongen): fine-tune gallery camera position"
```

---

### Task 8: Final polish and documentation

**Files:**
- Modify: `fabric/CLAUDE.md` or project `CLAUDE.md` to document the new task

**Step 1: Add documentation to CLAUDE.md**

Add to the "Icon Generation" section:

```markdown
### Gallery Image Generation

```bash
# Generate gallery/demo image (requires XVFB on Linux)
xvfb-run -a ./gradlew :fabric:generateGallery

# With custom dimensions
xvfb-run -a ./gradlew :fabric:generateGallery \
  -Dshulker_trims.gallery.width=2560 \
  -Dshulker_trims.gallery.height=1440
```

Output: `fabric/build/gallery/gallery.png`

The gallery shows 6 trimmed shulker boxes in a cozy storage room setting.
```

**Step 2: Commit documentation**

```bash
git add CLAUDE.md
git commit -m "docs: add gallery generation instructions"
```

---

## Iteration Notes

After Task 4, the remaining tasks (5-7) will likely need multiple iterations based on visual feedback. Use fresh subagents for each image assessment to maintain visual reasoning quality.

Key things to iterate on:
- Room structure (block choices, proportions)
- Shulker positions and orientations
- Camera angle and position
- Lighting (lantern placement)
- Props (add/remove as needed)
