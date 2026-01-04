# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Shulker Trims is a Minecraft mod that adds the ability to apply armor trim patterns to shulker boxes via the smithing table. It targets Minecraft 1.21.10 and supports both Fabric (client + server) and Bukkit/Paper (server-only plugin).

## Build Commands

```bash
./gradlew build           # Build all modules + universal JAR
./gradlew slowTest        # Run slow tests (game tests, etc.) - not included in build
./gradlew :fabric:build   # Build Fabric mod only
./gradlew :bukkit:build   # Build Bukkit plugin only
./gradlew :fabric:genSources  # Generate Minecraft sources for IDE support
```

Output JARs:
- `fabric/build/libs/shulker-trims-mc1.21.10-fabric-1.0.0.jar` — Fabric-only mod
- `bukkit/build/libs/shulker-trims-mc1.21.10-bukkit-1.0.0.jar` — Bukkit-only plugin
- `build/forgix/shulker-trims-1.0.0-mc1.21.10.jar` — Universal JAR (works on both platforms)

### Universal JAR

The universal JAR is built using [Forgix](https://github.com/PacifistMC/Forgix), which merges the Fabric mod and Bukkit plugin into a single distributable. It works by isolating platform-specific classes with renamed packages and including both `fabric.mod.json` and `plugin.yml` in the JAR root.

Drop the universal JAR into either a Fabric `mods/` folder or a Paper `plugins/` folder — the appropriate loader will use its entry point.

## Architecture

### Module Structure

- **common/** - Platform-agnostic code: `ShulkerTrim` data model and `TrimMaterials` color palette
- **fabric/** - Fabric mod with client rendering, mixins, and smithing recipes
- **bukkit/** - Paper plugin with NMS-based implementation and event handlers

### Key Design Decisions

1. **Storage**: Trims stored in `minecraft:custom_data` component as `{shulker_trims:trim: {pattern: "...", material: "..."}}`
2. **Cross-platform**: Shared NBT path enables world transfers between Fabric and Bukkit servers
3. **Rendering**: Uses vanilla's paletted permutations system (grayscale textures + runtime material color)

### Data Flow

**Applying trim**: Smithing table recipe → writes trim to item's `custom_data` component

**Block placement**: Vanilla transfers `custom_data` to block entity automatically; mixins read and cache trim

**Block breaking**: Mixin intercepts drop generation, reads trim from block entity, writes to dropped item

**Cross-platform sync (Paper→Fabric client)**: Plugin messaging on `shulker_trims:sync` channel sends trim data for client rendering

### Mixins (Fabric)

Server-side (`shulker_trims.mixins.json`):
- `ShulkerBoxBlockEntityMixin` - Adds trim storage to block entities
- `ShulkerBoxBlockMixin` - Transfers trim to dropped items
- `BlockPlacementDispenserBehaviorMixin` - Handles dispenser placement

Client-side (`shulker_trims.client.mixins.json`):
- `ShulkerBoxBlockEntityRendererMixin` - Renders trim overlay on placed shulkers
- `ShulkerBoxBlockEntityRenderStateMixin` - Adds trim field to render state
- `ItemModelManagerMixin` / `ShulkerBoxModelRendererMixin` - Item rendering with trim overlay

## Test Servers

- `run-server/` - Paper test server
- `run-server-fabric/` - Fabric test server

## Automated Testing

### Server GameTests (Fabric)

Server-side game tests verify trim data storage and manipulation:
- Located in `fabric/src/gametest/java/`
- Run with `./gradlew slowTest` or `./gradlew :fabric:runGameTest`
- Tests cover smithing recipes, block placement/breaking, dispenser behavior, etc.

### Client GameTests (Fabric)

Client-side game tests verify trim rendering on shulker boxes:
- Located in `fabric/src/gametest/java/` (class: `ShulkerTrimsClientGameTest`)
- Tests create singleplayer worlds, place trimmed shulkers, and take screenshots
- Run with `./gradlew :fabric:runClientGameTest` (requires display)
- Run with `./gradlew :fabric:runProductionClientGameTest` (uses XVFB in CI)

Screenshots are saved to `fabric/build/run/clientGameTest/screenshots/`

### Manual Testing

Manual testing covers recipe crafting, place/break cycles, dispenser/piston/explosion interactions, and cross-platform world transfers.

## CI Testing

The CI workflow (`.github/workflows/ci.yml`) runs automated tests for both platforms:

### Fabric Runtime Test
Uses [headlesshq/mc-runtime-test](https://github.com/headlesshq/mc-runtime-test) to verify the mod loads on a headless Minecraft client.

### Fabric Client GameTest
Runs client-side rendering tests using XVFB (X Virtual Frame Buffer) for headless screenshot capture. Screenshots are uploaded as CI artifacts for manual inspection.

### Paper Runtime Test
Uses a custom script (`.github/scripts/paper-runtime-test.sh`) that:
1. Downloads the latest Paper build for MC 1.21.10 from the official API
2. Configures a minimal server with the plugin installed
3. Starts the server and waits for the "Done" message
4. Verifies the plugin enabled successfully (checks for `[ShulkerTrims] Enabling ShulkerTrims`)
5. Checks for NMS compatibility errors (NoSuchMethodError, ClassNotFoundException, etc.)
6. Fails CI if the plugin doesn't load correctly

Run locally:
```bash
./gradlew :bukkit:build
.github/scripts/paper-runtime-test.sh bukkit/build/libs/shulker-trims-mc1.21.10-bukkit-1.0.0.jar 1.21.10
```

**Caveats:**
- The Paper runtime test only verifies plugin initialization, not gameplay mechanics
- NMS code compatibility is version-specific; the test catches reflection/method errors on startup
- Server startup takes ~20-30 seconds including world generation
