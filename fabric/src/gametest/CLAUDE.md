# Client Game Test Guidelines

## Player Positioning

When writing client game tests that involve player interactions with blocks (opening GUIs, clicking slots, etc.), **always place a solid platform for the player to stand on**.

Without a platform, the player will fall during multi-tick operations, moving out of range of the block and causing the GUI to close unexpectedly.

Example:
```java
// Place the block AND a platform
singleplayer.getServer().runOnServer(server -> {
    ServerWorld world = server.getOverworld();
    world.setBlockState(blockPos, Blocks.SMITHING_TABLE.getDefaultState());
    // Place barrier blocks as floor so the player doesn't fall
    world.setBlockState(blockPos.down(), Blocks.BARRIER.getDefaultState());
    world.setBlockState(blockPos.down().south(), Blocks.BARRIER.getDefaultState());
});
```

Barrier blocks are ideal for platforms as they're invisible and won't interfere with screenshots.
