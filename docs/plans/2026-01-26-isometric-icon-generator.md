# Isometric Icon Generator Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a Gradle task that generates isometric PNG renders of trimmed shulker boxes for use as project icons.

**Architecture:** New `icongen` source set with OrthoCamera dependency, implementing `FabricClientGameTest` to leverage Minecraft's renderer. Command-line arguments specify shulker color, trim pattern, and material combinations.

**Tech Stack:** Fabric API (client game test), OrthoCamera mod (orthographic projection), Loom (dependency management)

---

### Task 1: Create icongen source set structure

**Files:**
- Create: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/.gitkeep`
- Create: `fabric/src/icongen/resources/fabric.mod.json`

**Step 1: Create directory structure**

```bash
mkdir -p fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen
mkdir -p fabric/src/icongen/resources
```

**Step 2: Create minimal fabric.mod.json for icongen**

Create `fabric/src/icongen/resources/fabric.mod.json`:

```json
{
  "schemaVersion": 1,
  "id": "shulker_trims_icongen",
  "version": "1.0.0",
  "name": "Shulker Trims Icon Generator",
  "description": "Generates isometric icon renders for Shulker Trims mod",
  "authors": ["wlritchi"],
  "license": "CC0-1.0",
  "environment": "client",
  "entrypoints": {
    "fabric-client-gametest": [
      "com.wlritchi.shulkertrims.fabric.icongen.IconGenerator"
    ]
  },
  "depends": {
    "shulker_trims": "*",
    "orthocamera": "*",
    "fabricloader": ">=0.16.0",
    "minecraft": "1.21.10",
    "java": ">=21",
    "fabric-api": "*"
  }
}
```

**Step 3: Commit**

```bash
git add fabric/src/icongen/
git commit -m "feat(icongen): add source set directory structure"
```

---

### Task 2: Configure icongen source set in Gradle

**Files:**
- Modify: `fabric/build.gradle.kts`

**Step 1: Add icongen source set configuration**

Add after the `fabricApi { configureTests { ... } }` block (around line 151):

```kotlin
// Icon generation source set - separate from tests to isolate OrthoCamera dependency
sourceSets {
    create("icongen") {
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
        // Also need client classes for rendering
        compileClasspath += sourceSets.getByName("client").compileClasspath + sourceSets.getByName("client").output
        runtimeClasspath += sourceSets.getByName("client").runtimeClasspath + sourceSets.getByName("client").output
    }
}
```

**Step 2: Add Modrinth maven repository**

Add to the `repositories` block (after line 165):

```kotlin
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content {
            includeGroup("maven.modrinth")
        }
    }
```

**Step 3: Add OrthoCamera dependency**

Add to the `dependencies` block (after line 183):

```kotlin
    // OrthoCamera for isometric icon generation (isolated to icongen source set)
    "icongenModImplementation"("maven.modrinth:orthocamera:0.1.10+1.21.9")
```

**Step 4: Verify build compiles**

Run: `./gradlew :fabric:compileIcongenJava`
Expected: BUILD SUCCESSFUL (even though no Java files yet)

**Step 5: Commit**

```bash
git add fabric/build.gradle.kts
git commit -m "feat(icongen): configure source set with OrthoCamera dependency"
```

---

### Task 3: Create IconGenConfig class

**Files:**
- Create: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenConfig.java`

**Step 1: Create config parser**

```java
package com.wlritchi.shulkertrims.fabric.icongen;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Parses command-line arguments (passed as system properties) for icon generation.
 */
public class IconGenConfig {

    private static final String PREFIX = "shulker_trims.icongen.";

    private final List<String> colors;
    private final List<String> patterns;
    private final List<String> materials;
    private final Path outputDir;
    private final int size;

    public IconGenConfig() {
        this.colors = parseList("color", "purple");
        this.patterns = parseList("pattern", "sentry");
        this.materials = parseList("material", "gold");
        this.outputDir = Paths.get(System.getProperty(PREFIX + "output", "build/icons"));
        this.size = Integer.parseInt(System.getProperty(PREFIX + "size", "512"));
    }

    private static List<String> parseList(String key, String defaultValue) {
        String value = System.getProperty(PREFIX + key, defaultValue);
        return Arrays.asList(value.split(","));
    }

    public List<String> getColors() {
        return colors;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public List<String> getMaterials() {
        return materials;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public int getSize() {
        return size;
    }

    /**
     * Returns total number of icons to generate (Cartesian product).
     */
    public int getTotalCombinations() {
        return colors.size() * patterns.size() * materials.size();
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew :fabric:compileIcongenJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenConfig.java
git commit -m "feat(icongen): add IconGenConfig for CLI argument parsing"
```

---

### Task 4: Create IconGenerator class (skeleton)

**Files:**
- Create: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java`

**Step 1: Create skeleton implementation**

```java
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // TODO: Implement icon generation loop
        LOGGER.info("Icon generation complete (skeleton - no icons generated yet)");
    }
}
```

**Step 2: Verify compilation with OrthoCamera import**

Run: `./gradlew :fabric:compileIcongenJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java
git commit -m "feat(icongen): add IconGenerator skeleton with OrthoCamera import"
```

---

### Task 5: Add Gradle task to run icon generator

**Files:**
- Modify: `fabric/build.gradle.kts`

**Step 1: Register generateIcon task**

Add at the end of the file (after the `afterEvaluate` block):

```kotlin
// Icon generation task - runs icongen source set as a client game test
loom {
    runs {
        register("icongen") {
            client()
            name = "Generate Icons"
            source(sourceSets.getByName("icongen"))
            // Use the same configuration as client game tests
            property("fabric-api.gametest")
            property("fabric-api.gametest.client")
        }
    }
}

// Configure the icongen run task to pass through CLI arguments
afterEvaluate {
    tasks.named<JavaExec>("runIcongen") {
        // Pass through shulker_trims.icongen.* system properties
        System.getProperties().forEach { key, value ->
            if (key.toString().startsWith("shulker_trims.icongen.")) {
                jvmArgs("-D$key=$value")
            }
        }

        // Set default output directory
        val outputDir = project.layout.buildDirectory.dir("icons").get().asFile
        jvmArgs("-Dshulker_trims.icongen.output=${outputDir.absolutePath}")
    }
}

// Convenience task with clearer name
tasks.register("generateIcon") {
    group = "shulker trims"
    description = "Generate isometric icon renders of trimmed shulker boxes"
    dependsOn("runIcongen")
}
```

**Step 2: Verify task is registered**

Run: `./gradlew :fabric:tasks --group="shulker trims"`
Expected: Shows "generateIcon" task in list

**Step 3: Commit**

```bash
git add fabric/build.gradle.kts
git commit -m "feat(icongen): add generateIcon Gradle task"
```

---

### Task 6: Implement scene setup and OrthoCamera configuration

**Files:**
- Modify: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java`

**Step 1: Add scene setup method**

Add method to IconGenerator class:

```java
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

        // Configure OrthoCamera for isometric view
        configureOrthoCamera();

        // Position player/camera for isometric view
        // Camera looks at shulker from front-right, 45° yaw, 30° pitch
        float yaw = 225f;  // Looking toward -X, -Z (southwest in MC terms)
        float pitch = 30f; // Slight downward angle
        int cameraX = 3;
        int cameraY = 102;
        int cameraZ = 3;

        singleplayer.getServer().runCommand(
                String.format("tp @p %d %d %d %.1f %.1f", cameraX, cameraY, cameraZ, yaw, pitch)
        );
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
        config.fixed = true;
        config.setFixedYaw(225f);   // Match camera yaw
        config.setFixedPitch(30f);  // Match camera pitch
        config.setScaleX(2.5f);     // Zoom to frame shulker tightly
        config.setScaleY(2.5f);
        config.auto_third_person = false; // We'll handle camera mode ourselves
    }
```

**Step 2: Verify compilation**

Run: `./gradlew :fabric:compileIcongenJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java
git commit -m "feat(icongen): implement scene setup and OrthoCamera configuration"
```

---

### Task 7: Implement screenshot capture and saving

**Files:**
- Modify: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java`

**Step 1: Add screenshot capture method**

Add imports at top:

```java
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
```

Add method:

```java
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

        // Use Fabric's screenshot mechanism
        context.runOnClient(client -> {
            try {
                Framebuffer framebuffer = client.getFramebuffer();

                // Capture the framebuffer
                var nativeImage = ScreenshotRecorder.takeScreenshot(framebuffer);

                // Save to file
                nativeImage.writeTo(outputPath);
                nativeImage.close();

                LOGGER.info("Saved icon: {}", outputPath);
            } catch (IOException e) {
                LOGGER.error("Failed to save icon: {}", outputPath, e);
            }
        });

        context.waitTicks(2);
    }
```

**Step 2: Verify compilation**

Run: `./gradlew :fabric:compileIcongenJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java
git commit -m "feat(icongen): implement screenshot capture and saving"
```

---

### Task 8: Implement main generation loop

**Files:**
- Modify: `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java`

**Step 1: Update runTest method with generation loop**

Replace the `runTest` method:

```java
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
```

**Step 2: Verify compilation**

Run: `./gradlew :fabric:compileIcongenJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java
git commit -m "feat(icongen): implement main generation loop"
```

---

### Task 9: Add dependency verification checksums

**Files:**
- Modify: `gradle/verification-metadata.xml`

**Step 1: Fetch OrthoCamera checksum**

Run:
```bash
curl -sL "https://cdn.modrinth.com/data/MlLGIqhl/versions/QHG0ZU02/OrthoCamera-0.1.10%2B1.21.9.jar" | sha256sum
```

**Step 2: Add checksum to verification-metadata.xml**

Add entry for OrthoCamera (exact format depends on current file structure - will need to inspect and match).

**Step 3: Verify build with verification**

Run: `./gradlew :fabric:compileIcongenJava --refresh-dependencies`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add gradle/verification-metadata.xml
git commit -m "build: add OrthoCamera dependency verification checksum"
```

---

### Task 10: Test end-to-end icon generation

**Step 1: Run icon generation with default settings**

Run:
```bash
xvfb-run -a ./gradlew :fabric:generateIcon
```
Expected: Creates `fabric/build/icons/shulker-purple-sentry-gold.png`

**Step 2: Verify the generated icon**

Open `fabric/build/icons/shulker-purple-sentry-gold.png` and verify:
- Shows a shulker box from isometric angle
- Has trim overlay visible
- Image is 512x512 pixels

**Step 3: Test with multiple combinations**

Run:
```bash
xvfb-run -a ./gradlew :fabric:generateIcon \
  -Dshulker_trims.icongen.color=cyan,purple \
  -Dshulker_trims.icongen.pattern=sentry,tide \
  -Dshulker_trims.icongen.material=gold,diamond
```
Expected: Creates 8 icons in `fabric/build/icons/`

**Step 4: Commit any fixes needed**

If issues are found during testing, fix and commit with appropriate messages.

---

### Task 11: Fine-tune camera angles and lighting

**Step 1: Review generated icons**

Examine the icons for:
- Is the shulker centered in frame?
- Is the isometric angle correct (~30° pitch, 45° yaw)?
- Is the lighting even on all faces?
- Is the zoom level appropriate?

**Step 2: Adjust OrthoCamera parameters if needed**

Modify `configureOrthoCamera()` method based on visual review:
- `setScaleX/setScaleY` - adjust zoom
- `setFixedYaw/setFixedPitch` - adjust viewing angle
- Camera position in `setupScene()` - adjust framing

**Step 3: Test and iterate**

Re-run generation and verify improvements.

**Step 4: Commit final tuning**

```bash
git add fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java
git commit -m "feat(icongen): fine-tune camera angles and framing"
```

---

### Task 12: Document usage in CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Add icon generation section**

Add to CLAUDE.md under a new "## Icon Generation" section:

```markdown
## Icon Generation

Generate isometric renders of trimmed shulker boxes for project icons:

```bash
# Generate single icon (defaults: purple shulker, sentry trim, gold material)
xvfb-run -a ./gradlew :fabric:generateIcon

# Generate with specific options
xvfb-run -a ./gradlew :fabric:generateIcon \
  -Dshulker_trims.icongen.color=cyan \
  -Dshulker_trims.icongen.pattern=tide \
  -Dshulker_trims.icongen.material=diamond

# Generate multiple combinations (Cartesian product)
xvfb-run -a ./gradlew :fabric:generateIcon \
  -Dshulker_trims.icongen.color=cyan,purple,blue \
  -Dshulker_trims.icongen.pattern=sentry,tide \
  -Dshulker_trims.icongen.material=gold,diamond

# Custom size and output directory
xvfb-run -a ./gradlew :fabric:generateIcon \
  -Dshulker_trims.icongen.size=1024 \
  -Dshulker_trims.icongen.output=path/to/output
```

Output files: `fabric/build/icons/shulker-<color>-<pattern>-<material>.png`

Available colors: `default`, `white`, `orange`, `magenta`, `light_blue`, `yellow`, `lime`, `pink`, `gray`, `light_gray`, `cyan`, `purple`, `blue`, `brown`, `green`, `red`, `black`
```

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add icon generation usage instructions"
```

---

## Summary

This plan creates:

1. New `icongen` source set isolated from tests
2. OrthoCamera dependency for orthographic projection
3. `IconGenConfig` for parsing CLI arguments
4. `IconGenerator` implementing the generation flow
5. `generateIcon` Gradle task
6. Documentation for usage

The implementation leverages existing patterns from `ShulkerTrimsClientGameTest` while keeping the OrthoCamera dependency isolated to the icongen source set.
