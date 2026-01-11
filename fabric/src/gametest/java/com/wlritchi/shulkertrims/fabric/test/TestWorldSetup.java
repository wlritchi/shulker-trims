package com.wlritchi.shulkertrims.fabric.test;

import java.util.List;

/**
 * Shared test world configuration that can be used across singleplayer and multiplayer tests.
 * Defines the exact positions, patterns, colors, and materials for test shulker boxes.
 *
 * <p>This ensures that both Fabric singleplayer tests and cross-platform server tests
 * use identical world setups, allowing golden screenshots to be compared across platforms.
 */
public final class TestWorldSetup {

    private TestWorldSetup() {}

    // Grid layout constants
    public static final int GRID_COLS = 6;
    public static final int GRID_ROWS = 3;
    public static final int SPACING = 3;
    public static final int BASE_Y = 100;

    // Camera position constants
    public static final int CAMERA_X = (GRID_COLS - 1) * SPACING + 1;  // 16
    public static final int CAMERA_Y = BASE_Y + 4;                      // 104
    public static final int CAMERA_Z = (GRID_ROWS - 1) * SPACING + 3;  // 9
    public static final float CAMERA_YAW = 135.0f;
    public static final float CAMERA_PITCH = 35.0f;

    // Platform position for player to stand on
    public static final int PLATFORM_X = CAMERA_X;
    public static final int PLATFORM_Y = BASE_Y + 3;
    public static final int PLATFORM_Z = CAMERA_Z;

    // All 18 trim patterns in order
    public static final List<String> ALL_PATTERNS = List.of(
            "minecraft:sentry", "minecraft:vex", "minecraft:wild", "minecraft:coast",
            "minecraft:dune", "minecraft:wayfinder", "minecraft:raiser", "minecraft:shaper",
            "minecraft:host", "minecraft:ward", "minecraft:silence", "minecraft:tide",
            "minecraft:snout", "minecraft:rib", "minecraft:eye", "minecraft:spire",
            "minecraft:flow", "minecraft:bolt"
    );

    // All shulker box block IDs (undyed + 16 colors)
    public static final List<String> ALL_SHULKER_BLOCKS = List.of(
            "minecraft:shulker_box",           // 0: undyed (purple-ish)
            "minecraft:white_shulker_box",     // 1
            "minecraft:orange_shulker_box",    // 2
            "minecraft:magenta_shulker_box",   // 3
            "minecraft:light_blue_shulker_box",// 4
            "minecraft:yellow_shulker_box",    // 5
            "minecraft:lime_shulker_box",      // 6
            "minecraft:pink_shulker_box",      // 7
            "minecraft:gray_shulker_box",      // 8
            "minecraft:light_gray_shulker_box",// 9
            "minecraft:cyan_shulker_box",      // 10
            "minecraft:purple_shulker_box",    // 11
            "minecraft:blue_shulker_box",      // 12
            "minecraft:brown_shulker_box",     // 13
            "minecraft:green_shulker_box",     // 14
            "minecraft:red_shulker_box",       // 15
            "minecraft:black_shulker_box"      // 16
    );

    // Contrasting materials for each shulker color (chosen for visibility)
    public static final List<String> CONTRASTING_MATERIALS = List.of(
            "minecraft:quartz",     // 0: Undyed (purple) - white on purple
            "minecraft:netherite",  // 1: White - dark on white
            "minecraft:lapis",      // 2: Orange - blue on orange
            "minecraft:gold",       // 3: Magenta - yellow on magenta
            "minecraft:redstone",   // 4: Light Blue - red on light blue
            "minecraft:amethyst",   // 5: Yellow - purple on yellow
            "minecraft:copper",     // 6: Lime - brown on lime
            "minecraft:emerald",    // 7: Pink - green on pink
            "minecraft:diamond",    // 8: Gray - cyan on gray
            "minecraft:redstone",   // 9: Light Gray - red on light gray
            "minecraft:netherite",  // 10: Cyan - dark on cyan
            "minecraft:gold",       // 11: Purple - yellow on purple
            "minecraft:copper",     // 12: Blue - brown on blue
            "minecraft:diamond",    // 13: Brown - cyan on brown
            "minecraft:amethyst",   // 14: Green - purple on green
            "minecraft:quartz",     // 15: Red - white on red
            "minecraft:emerald",    // 16: Black - green on black
            "minecraft:iron"        // 17: Undyed (repeat) - silver on purple
    );

    /**
     * Represents a single shulker box placement in the test grid.
     */
    public record ShulkerPlacement(
            int x, int y, int z,
            String blockId,
            String pattern,
            String material,
            int gridCol, int gridRow
    ) {}

    /**
     * Get all 18 shulker placements for the comprehensive world test.
     */
    public static List<ShulkerPlacement> getComprehensiveWorldPlacements() {
        return java.util.stream.IntStream.range(0, 18)
                .mapToObj(i -> {
                    int row = i / GRID_COLS;
                    int col = i % GRID_COLS;
                    int x = col * SPACING;
                    int z = row * SPACING;
                    return new ShulkerPlacement(
                            x, BASE_Y, z,
                            ALL_SHULKER_BLOCKS.get(i % ALL_SHULKER_BLOCKS.size()),
                            ALL_PATTERNS.get(i),
                            CONTRASTING_MATERIALS.get(i),
                            col, row
                    );
                })
                .toList();
    }

    /**
     * Generate commands to set up the test world.
     * These commands can be run on any server (Fabric or Paper) via RCON or console.
     *
     * @return List of commands to execute in order
     */
    public static List<String> generateSetupCommands() {
        var commands = new java.util.ArrayList<String>();

        // Set time to noon for consistent lighting
        commands.add("time set noon");

        // Disable weather
        commands.add("weather clear 1000000");

        // Place barrier platform for player
        commands.add(String.format("setblock %d %d %d minecraft:barrier",
                PLATFORM_X, PLATFORM_Y, PLATFORM_Z));

        // Place all shulker boxes with trims
        for (ShulkerPlacement placement : getComprehensiveWorldPlacements()) {
            // Place the shulker box
            commands.add(String.format("setblock %d %d %d %s",
                    placement.x(), placement.y(), placement.z(), placement.blockId()));

            // Apply trim using /data merge on the block entity
            // In MC 1.21+, block entity component data is stored under "components"
            // The path is: components."minecraft:custom_data"."shulker_trims:trim"
            commands.add(String.format(
                    "data merge block %d %d %d {components:{\"minecraft:custom_data\":{\"shulker_trims:trim\":{pattern:\"%s\",material:\"%s\"}}}}",
                    placement.x(), placement.y(), placement.z(),
                    placement.pattern(), placement.material()));
        }

        return commands;
    }

    /**
     * Generate basic setup commands (no trims) for testing world setup.
     * Useful for verifying RCON connectivity before adding complex trim data.
     *
     * @return List of commands to execute in order
     */
    public static List<String> generateBasicSetupCommands() {
        var commands = new java.util.ArrayList<String>();

        // Disable mob spawning completely
        commands.add("gamerule doMobSpawning false");

        // Set time to noon for consistent lighting
        commands.add("time set noon");

        // Disable weather
        commands.add("weather clear 1000000");

        // Define test area bounds
        int floorMinX = -2;
        int floorMaxX = GRID_COLS * SPACING + 2;
        int floorMinZ = -2;
        int floorMaxZ = GRID_ROWS * SPACING + 2;

        // Force-load chunks around the test area so they're available immediately
        // Without this, the test area chunks might not be loaded when player teleports
        int chunkMinX = floorMinX >> 4;  // Convert block coords to chunk coords
        int chunkMaxX = floorMaxX >> 4;
        int chunkMinZ = floorMinZ >> 4;
        int chunkMaxZ = floorMaxZ >> 4;
        commands.add(String.format("forceload add %d %d %d %d",
                chunkMinX * 16, chunkMinZ * 16, chunkMaxX * 16, chunkMaxZ * 16));

        // Set world spawn point near our test area so players spawn where chunks are loaded
        // This is critical for the test - if player spawns far away, test area chunks won't load
        commands.add(String.format("setworldspawn %d %d %d", CAMERA_X, CAMERA_Y, CAMERA_Z));

        // Place barrier floor under the entire test area for safety
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                floorMinX, BASE_Y - 1, floorMinZ, floorMaxX, BASE_Y - 1, floorMaxZ));

        // Place barrier platform for player at camera position (3x3 area for stability)
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                PLATFORM_X - 1, PLATFORM_Y, PLATFORM_Z - 1,
                PLATFORM_X + 1, PLATFORM_Y, PLATFORM_Z + 1));

        // Place shulker boxes without trims (just to verify world setup works)
        for (ShulkerPlacement placement : getComprehensiveWorldPlacements()) {
            commands.add(String.format("setblock %d %d %d %s",
                    placement.x(), placement.y(), placement.z(), placement.blockId()));
        }

        return commands;
    }

    /**
     * Generate the teleport command for camera positioning.
     */
    public static String generateTeleportCommand(String playerSelector) {
        return String.format("tp %s %d %d %d %.1f %.1f",
                playerSelector, CAMERA_X, CAMERA_Y, CAMERA_Z, CAMERA_YAW, CAMERA_PITCH);
    }

    /**
     * Generate commands for an operator player to set up their view.
     */
    public static List<String> generatePlayerSetupCommands(String playerSelector) {
        return List.of(
                "gamemode creative " + playerSelector,
                generateTeleportCommand(playerSelector)
        );
    }
}
