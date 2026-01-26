# Client Game Test Guidelines

## Multiplayer Golden Screenshot Testing Strategy

### Goals

The multiplayer tests in `ExternalServerConnectionTest` verify that our Fabric client correctly
receives and renders trim data when connected to external servers (Paper, and eventually Fabric
dedicated servers). These tests are critical because:

1. **Cross-platform sync validation**: The Paper plugin uses a completely different sync mechanism
   (plugin messaging) than singleplayer (direct mixin access). Golden screenshot tests ensure the
   visual result is identical.

2. **Protocol-level testing**: We want to verify that real Minecraft protocol interactions work
   correctly, not just that commands can set data via RCON.

3. **Multi-player scenarios**: Tests like the two-player placement test verify that when one player
   places a trimmed shulker, other nearby players see the trim correctly.

### Acceptable Compromises

These trade-offs are intentional and acceptable:

- **Hardcoded wait times** (`waitTicks(40)`, `waitTicks(100)`, etc.): For async operations like
  chunk loading and sync propagation, polling for specific conditions adds complexity without
  improving correctness. Lazy delays for the expected maximum time are simpler and reliable.

- **Shared server instance**: Restarting Paper for each test adds ~30s overhead. Cleaning up via
  RCON between tests provides sufficient isolation with minimal complexity.

- **Waiting for toasts/particles to disappear**: Suppressing recipe unlock toasts or dispenser
  smoke particles would require invasive changes. Waiting a few seconds is simpler.

### Non-Negotiable Requirements

These MUST work correctly via real protocol interactions:

- **Block placement via MCProtocolLib**: The two-player test exists specifically to verify that
  when a player places a trimmed shulker box via normal gameplay, the trim syncs to observers.
  Falling back to RCON `setblock`/`data merge` bypasses the exact code path we're trying to test.
  If MCProtocolLib placement doesn't work, we need to fix it—not work around it.

- **Inventory/hotbar sync**: When we give items to a bot player, the bot must be able to use those
  items via protocol packets. If the server doesn't recognize the bot's inventory state, that's a
  bug in our test setup.

### Player Positioning Strategy

For **observation tests** (world rendering, dispenser output, two-player placement):
- Use **spectator mode** for the observer client
- Teleport to camera position while in spectator mode (prevents falling)
- No barrier platforms needed for spectator-mode observers

For **interaction tests** (chest GUI, smithing table):
- Player must be in creative/survival mode to interact with blocks
- Place barrier floors at the interaction location
- Teleport should position player correctly above the floor

Do NOT mix these approaches within a single test. Avoid:
- Toggling between spectator and creative to work around positioning issues
- Teleport retry logic with mode switches
- Barrier platforms for spectator-mode observers

## Singleplayer Test Guidelines

### Player Positioning

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
