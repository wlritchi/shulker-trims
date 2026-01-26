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

        // Disable daylight cycle and set time to noon for consistent lighting
        commands.add("gamerule doDaylightCycle false");
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

    // Chest GUI test constants
    public static final int CHEST_X = 0;
    public static final int CHEST_Y = 100;
    public static final int CHEST_Z = 0;

    // Smithing table test constants
    public static final int SMITHING_TABLE_X = 0;
    public static final int SMITHING_TABLE_Y = 100;
    public static final int SMITHING_TABLE_Z = 0;

    // Dispenser placement test constants
    // Dispenser faces UP so shulker appears upright above it
    public static final int DISPENSER_X = 0;
    public static final int DISPENSER_Y = 99;   // Dispenser position (below output)
    public static final int DISPENSER_Z = 0;
    public static final int DISPENSER_OUTPUT_Y = 100;  // Where shulker lands (above dispenser)
    // Player camera position to view the dispenser output
    // Position player south of the shulker, looking north at it
    public static final int DISPENSER_CAMERA_X = 0;
    public static final int DISPENSER_CAMERA_Y = 102;  // 2 blocks above output for good view
    public static final int DISPENSER_CAMERA_Z = 3;    // South of the shulker
    public static final float DISPENSER_CAMERA_YAW = 180.0f;  // Looking north toward shulker
    public static final float DISPENSER_CAMERA_PITCH = 30.0f;  // Looking down at shulker

    /**
     * Generate commands to set up a chest filled with trimmed shulker boxes.
     * Uses the same pattern/color/material combinations as the world test.
     *
     * @return List of commands to execute in order
     */
    public static List<String> generateChestGuiSetupCommands() {
        var commands = new java.util.ArrayList<String>();

        // Set time to noon and disable daylight cycle for consistent lighting
        commands.add("gamerule doDaylightCycle false");
        commands.add("time set noon");

        // Force-load the chunk containing the test area
        commands.add(String.format("forceload add %d %d", CHEST_X, CHEST_Z));

        // Set world spawn near test area so chunks load
        commands.add(String.format("setworldspawn %d %d %d", CHEST_X, CHEST_Y, CHEST_Z + 1));

        // Place barrier floor for player (larger area for stability)
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                CHEST_X - 1, CHEST_Y - 1, CHEST_Z - 1,
                CHEST_X + 1, CHEST_Y - 1, CHEST_Z + 2));

        // Place chest
        commands.add(String.format("setblock %d %d %d minecraft:chest", CHEST_X, CHEST_Y, CHEST_Z));

        // Fill chest with trimmed shulkers
        // In MC 1.21+, we use /item modify or /item replace to set items with components
        for (int i = 0; i < 18; i++) {
            String shulkerItemId = ALL_SHULKER_BLOCKS.get(i % ALL_SHULKER_BLOCKS.size())
                    .replace("minecraft:", "");  // e.g., "shulker_box", "white_shulker_box"
            String pattern = ALL_PATTERNS.get(i);
            String material = CONTRASTING_MATERIALS.get(i);

            // Use /item replace with custom_data component
            // Format: item replace block <pos> container.<slot> with <item>[custom_data={...}]
            String itemCommand = String.format(
                    "item replace block %d %d %d container.%d with %s[custom_data={\"shulker_trims:trim\":{pattern:\"%s\",material:\"%s\"}}]",
                    CHEST_X, CHEST_Y, CHEST_Z, i, shulkerItemId, pattern, material);
            commands.add(itemCommand);
        }

        return commands;
    }

    /**
     * Generate commands to set up a smithing table with trim preview items.
     * Uses blue shulker box + wild pattern + redstone material for high contrast.
     *
     * @param playerSelector The player to give items to
     * @return List of commands to execute in order
     */
    public static List<String> generateSmithingTableSetupCommands(String playerSelector) {
        var commands = new java.util.ArrayList<String>();

        // Set time to noon and disable daylight cycle for consistent lighting
        commands.add("gamerule doDaylightCycle false");
        commands.add("time set noon");

        // Force-load the chunk containing the test area
        commands.add(String.format("forceload add %d %d", SMITHING_TABLE_X, SMITHING_TABLE_Z));

        // Set world spawn near test area so chunks load
        commands.add(String.format("setworldspawn %d %d %d", SMITHING_TABLE_X, SMITHING_TABLE_Y, SMITHING_TABLE_Z + 1));

        // Place barrier floor for player (larger area for stability)
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                SMITHING_TABLE_X - 1, SMITHING_TABLE_Y - 1, SMITHING_TABLE_Z - 1,
                SMITHING_TABLE_X + 1, SMITHING_TABLE_Y - 1, SMITHING_TABLE_Z + 2));

        // Place smithing table
        commands.add(String.format("setblock %d %d %d minecraft:smithing_table",
                SMITHING_TABLE_X, SMITHING_TABLE_Y, SMITHING_TABLE_Z));

        // Give player items in hotbar (same as singleplayer test)
        commands.add("item replace entity " + playerSelector + " hotbar.0 with wild_armor_trim_smithing_template 1");
        commands.add("item replace entity " + playerSelector + " hotbar.1 with blue_shulker_box 1");
        commands.add("item replace entity " + playerSelector + " hotbar.2 with redstone 1");

        return commands;
    }

    /**
     * Generate commands to set up a dispenser with a trimmed shulker box.
     * Uses blue shulker box + wild pattern + redstone material for visibility.
     *
     * <p>The dispenser is placed facing UP, so when activated it will place
     * a shulker box on the block above it (upright orientation). This tests
     * the Paper plugin's sync mechanism for dispenser-placed blocks.
     *
     * @return List of commands to execute in order
     */
    public static List<String> generateDispenserSetupCommands() {
        var commands = new java.util.ArrayList<String>();

        // Set time to noon and disable daylight cycle for consistent lighting
        commands.add("gamerule doDaylightCycle false");
        commands.add("time set noon");

        // Force-load the chunk containing the test area
        commands.add(String.format("forceload add %d %d", DISPENSER_X, DISPENSER_Z));

        // Set world spawn near test area so chunks load
        commands.add(String.format("setworldspawn %d %d %d",
                DISPENSER_X, DISPENSER_OUTPUT_Y, DISPENSER_Z));

        // Place barrier floor under the dispenser
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                DISPENSER_X - 1, DISPENSER_Y - 1, DISPENSER_Z - 1,
                DISPENSER_X + 1, DISPENSER_Y - 1, DISPENSER_Z + 1));

        // Place barrier platform for player at camera position
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                DISPENSER_CAMERA_X - 1, DISPENSER_CAMERA_Y - 1, DISPENSER_CAMERA_Z - 1,
                DISPENSER_CAMERA_X + 1, DISPENSER_CAMERA_Y - 1, DISPENSER_CAMERA_Z + 1));

        // Place dispenser facing UP so shulker box appears upright above it
        // In MC, dispenser facing: 0=down, 1=up, 2=north, 3=south, 4=west, 5=east
        commands.add(String.format("setblock %d %d %d minecraft:dispenser[facing=up]",
                DISPENSER_X, DISPENSER_Y, DISPENSER_Z));

        // Put a trimmed blue shulker box item in the dispenser
        // Uses wild pattern + redstone material for good contrast
        String dispenserItemCommand = String.format(
                "item replace block %d %d %d container.0 with blue_shulker_box[custom_data={\"shulker_trims:trim\":{pattern:\"minecraft:wild\",material:\"minecraft:redstone\"}}]",
                DISPENSER_X, DISPENSER_Y, DISPENSER_Z);
        commands.add(dispenserItemCommand);

        return commands;
    }

    /**
     * Generate the command to fire the dispenser by placing a redstone block.
     *
     * @return The command to execute
     */
    public static String generateFireDispenserCommand() {
        // Place redstone block adjacent to dispenser to power it
        return String.format("setblock %d %d %d minecraft:redstone_block",
                DISPENSER_X + 1, DISPENSER_Y, DISPENSER_Z);
    }

    /**
     * Generate teleport command for dispenser test camera position.
     */
    public static String generateDispenserTeleportCommand(String playerSelector) {
        return String.format("tp %s %d %d %d %.1f %.1f",
                playerSelector, DISPENSER_CAMERA_X, DISPENSER_CAMERA_Y, DISPENSER_CAMERA_Z,
                DISPENSER_CAMERA_YAW, DISPENSER_CAMERA_PITCH);
    }

    // Two-player placement test constants
    // Tests that a shulker box placed by one player syncs trims to another player's client
    // Uses X=20 to avoid coordinate conflicts with dispenser test at (0, 100, 0)
    // This prevents stale lastKnownTrims state from causing sync detection issues
    public static final int TWO_PLAYER_BLOCK_X = 20;
    public static final int TWO_PLAYER_BLOCK_Y = 100;
    public static final int TWO_PLAYER_BLOCK_Z = 0;
    // Position for the player who places the block (north of the placement spot)
    public static final int TWO_PLAYER_PLACER_X = 20;
    public static final int TWO_PLAYER_PLACER_Y = 100;
    public static final int TWO_PLAYER_PLACER_Z = -2;
    // Position for the observer player (south of the block, looking north)
    public static final int TWO_PLAYER_OBSERVER_X = 20;
    public static final int TWO_PLAYER_OBSERVER_Y = 102;
    public static final int TWO_PLAYER_OBSERVER_Z = 3;
    public static final float TWO_PLAYER_OBSERVER_YAW = 180.0f;  // Looking north
    public static final float TWO_PLAYER_OBSERVER_PITCH = 30.0f;  // Looking down

    /**
     * Generate commands to set up the two-player placement test.
     * Sets up floor, forceloads chunks, but does NOT give items to the bot.
     * The bot's items should be given after it connects.
     *
     * @return List of commands to execute in order
     */
    public static List<String> generateTwoPlayerSetupCommands() {
        var commands = new java.util.ArrayList<String>();

        // Set time to noon and disable daylight cycle for consistent lighting
        commands.add("gamerule doDaylightCycle false");
        commands.add("time set noon");

        // Force-load the chunk containing the test area
        commands.add(String.format("forceload add %d %d", TWO_PLAYER_BLOCK_X, TWO_PLAYER_BLOCK_Z));

        // Set world spawn near test area so chunks load
        commands.add(String.format("setworldspawn %d %d %d",
                TWO_PLAYER_BLOCK_X, TWO_PLAYER_BLOCK_Y, TWO_PLAYER_BLOCK_Z));

        // Place barrier floor for placement area
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                TWO_PLAYER_BLOCK_X - 2, TWO_PLAYER_BLOCK_Y - 1, TWO_PLAYER_BLOCK_Z - 3,
                TWO_PLAYER_BLOCK_X + 2, TWO_PLAYER_BLOCK_Y - 1, TWO_PLAYER_BLOCK_Z + 4));

        // Place barrier platform for observer at camera position
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                TWO_PLAYER_OBSERVER_X - 1, TWO_PLAYER_OBSERVER_Y - 1, TWO_PLAYER_OBSERVER_Z - 1,
                TWO_PLAYER_OBSERVER_X + 1, TWO_PLAYER_OBSERVER_Y - 1, TWO_PLAYER_OBSERVER_Z + 1));

        return commands;
    }

    /**
     * Generate command to give the placer bot a trimmed shulker box.
     * Uses blue shulker box + wild pattern + redstone material for visibility.
     *
     * @param playerSelector The bot player to give the item to
     * @return The command to give the item
     */
    public static String generateTwoPlayerGiveItemCommand(String playerSelector) {
        return String.format(
                "item replace entity %s hotbar.0 with blue_shulker_box[custom_data={\"shulker_trims:trim\":{pattern:\"minecraft:wild\",material:\"minecraft:redstone\"}}]",
                playerSelector);
    }

    /**
     * Generate teleport command for the observer in the two-player test.
     */
    public static String generateTwoPlayerObserverTeleportCommand(String playerSelector) {
        return String.format("tp %s %d %d %d %.1f %.1f",
                playerSelector, TWO_PLAYER_OBSERVER_X, TWO_PLAYER_OBSERVER_Y, TWO_PLAYER_OBSERVER_Z,
                TWO_PLAYER_OBSERVER_YAW, TWO_PLAYER_OBSERVER_PITCH);
    }

    /**
     * Generate teleport command for the placer bot in the two-player test.
     */
    public static String generateTwoPlayerPlacerTeleportCommand(String playerSelector) {
        return String.format("tp %s %d %d %d 0 0",
                playerSelector, TWO_PLAYER_PLACER_X, TWO_PLAYER_PLACER_Y, TWO_PLAYER_PLACER_Z);
    }

    /**
     * Generate teleport command to chest/smithing table position for GUI tests.
     */
    public static String generateGuiTeleportCommand(String playerSelector, int x, int y, int z) {
        // Teleport to just south of the block, facing north
        return String.format("tp %s %d %d %d 0 0", playerSelector, x, y, z + 1);
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

    /**
     * Generate commands to clean up/reset the test world.
     * Removes all blocks in the test area, restoring it to air/barrier floor.
     * Should be called between tests to ensure a clean state.
     *
     * @param playerSelector The player whose inventory should be cleared
     * @return List of commands to execute in order
     */
    public static List<String> generateCleanupCommands(String playerSelector) {
        var commands = new java.util.ArrayList<String>();

        // Define test area bounds (covers both grid test area and GUI test area)
        int minX = -2;
        int maxX = GRID_COLS * SPACING + 2;
        int minZ = -2;
        int maxZ = GRID_ROWS * SPACING + 2;
        int minY = BASE_Y - 1;
        int maxY = BASE_Y + 5;

        // Fill the entire test area with air (except floor)
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:air",
                minX, BASE_Y, minZ, maxX, maxY, maxZ));

        // Restore barrier floor
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                minX, BASE_Y - 1, minZ, maxX, BASE_Y - 1, maxZ));

        // Restore player platform for camera position (3x3)
        commands.add(String.format("fill %d %d %d %d %d %d minecraft:barrier",
                PLATFORM_X - 1, PLATFORM_Y, PLATFORM_Z - 1,
                PLATFORM_X + 1, PLATFORM_Y, PLATFORM_Z + 1));

        // Clear player inventory
        commands.add("clear " + playerSelector);

        // Reset player to safe position
        commands.add(String.format("tp %s %d %d %d 0 0",
                playerSelector, PLATFORM_X, PLATFORM_Y + 1, PLATFORM_Z));

        return commands;
    }
}
