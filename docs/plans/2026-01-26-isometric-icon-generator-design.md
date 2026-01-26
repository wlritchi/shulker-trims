# Isometric Icon Generator Design

## Overview

Automated generation of isometric shulker box renders for use as project icons (Modrinth, mod menu). Uses OrthoCamera mod for orthographic projection within the Fabric game test client infrastructure.

## Goals

- Generate 512x512 PNG images with transparent backgrounds
- Support any combination of shulker color, trim pattern, and trim material
- Automate generation via Gradle task with command-line arguments
- Stay true to in-game rendering by leveraging actual Minecraft renderer

## Architecture

### Source Set Structure

New `icongen` source set in the fabric module, isolated from main code and test suite:

```
fabric/src/icongen/
├── java/com/wlritchi/shulkertrims/fabric/icongen/
│   ├── IconGenerator.java      # Main generation logic
│   └── IconGenConfig.java      # CLI argument parsing
└── resources/
    └── fabric.mod.json         # Minimal mod descriptor
```

### Dependencies

- Main `shulker_trims` mod (for trim rendering)
- OrthoCamera `0.1.9+1.21.9` from Modrinth (for orthographic projection)
- Fabric Client GameTest API (for screenshot capture)

## Command-Line Interface

```bash
./gradlew generateIcon \
  --color=cyan,magenta \
  --pattern=sentry,tide \
  --material=gold,iron
```

### Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `--color` | `purple` | Shulker box color(s), comma-separated |
| `--pattern` | `sentry` | Trim pattern(s), comma-separated |
| `--material` | `gold` | Trim material(s), comma-separated |
| `--output` | `build/icons` | Output directory |
| `--size` | `512` | Image size in pixels (square) |

Arguments support comma-separated values; the Cartesian product of all combinations is generated.

### Output

Files are named descriptively:
```
build/icons/shulker-cyan-sentry-gold.png
build/icons/shulker-magenta-tide-iron.png
```

## Icon Generation Flow

1. Parse system properties for colors, patterns, materials
2. Create superflat void singleplayer world
3. Configure OrthoCamera:
   - Enable orthographic mode
   - Fix camera at ~30° pitch, 45° yaw (Minecraft-style isometric)
   - Set scale to frame shulker tightly
4. For each combination:
   - Place shulker box at origin
   - Apply trim via `TrimmedShulkerBox.shulkerTrims$setTrim()`
   - Set time to noon, disable weather
   - Maximize ambient lighting
   - Wait for rendering to stabilize
   - Capture screenshot at target resolution
   - Post-process for transparent background
   - Save to output directory

## Technical Details

### OrthoCamera Integration

Programmatically configure via static config:
```java
OrthoCamera.CONFIG.enabled = true;
OrthoCamera.CONFIG.fixed = true;
OrthoCamera.CONFIG.fixed_yaw = 45.0f;
OrthoCamera.CONFIG.fixed_pitch = 30.0f;
OrthoCamera.CONFIG.scale_x = 2.5f;
OrthoCamera.CONFIG.scale_y = 2.5f;
```

### Screenshot Capture

Use Fabric's test screenshot API for capture. Investigate whether it supports direct capture without comparison, otherwise use `Screenshot.grab()`.

### Transparent Background

Render against a solid "greenscreen" color (e.g., magenta `#FF00FF`), then post-process to replace with transparency. Can be done in Java before saving or via external tool.

### Lighting

- Set game time to noon
- Apply night vision effect or maximize gamma
- May add post-processing to dim right-side face for depth (future enhancement)

## Build Configuration

```kotlin
// fabric/build.gradle.kts

sourceSets {
    create("icongen") {
        compileClasspath += main.get().compileClasspath + main.get().output
        runtimeClasspath += main.get().runtimeClasspath + main.get().output
    }
}

dependencies {
    "icongenModImplementation"("maven.modrinth:orthocamera:0.1.9+1.21.9")
}

tasks.register<JavaExec>("generateIcon") {
    dependsOn(tasks.named("icongenClasses"))
    // Configure Fabric test client with icongen entrypoint
    // Pass CLI arguments as system properties
}
```

## Files to Create

- `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenerator.java`
- `fabric/src/icongen/java/com/wlritchi/shulkertrims/fabric/icongen/IconGenConfig.java`
- `fabric/src/icongen/resources/fabric.mod.json`
- Updates to `fabric/build.gradle.kts`

## Future Enhancements

- Lighting adjustments (dim right-side face for depth)
- Fine-tune camera angles based on visual review
- Composite grid output for comparing multiple variants
- Animation support (rotating view, opening shulker)
