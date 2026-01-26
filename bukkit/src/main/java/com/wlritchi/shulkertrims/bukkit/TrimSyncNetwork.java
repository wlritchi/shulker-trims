package com.wlritchi.shulkertrims.bukkit;

import com.wlritchi.shulkertrims.common.ShulkerTrim;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles syncing trim data to Fabric clients via plugin messaging.
 *
 * Packet format (single trim sync):
 * - int: block X position
 * - int: block Y position
 * - int: block Z position
 * - boolean: has trim
 * - if has trim:
 *   - UTF string: pattern (e.g., "minecraft:sentry")
 *   - UTF string: material (e.g., "minecraft:redstone")
 */
public class TrimSyncNetwork implements PluginMessageListener {

    public static final String CHANNEL = "shulker_trims:sync";

    private final Plugin plugin;

    /**
     * Tracks the last known trim state for each shulker box location.
     * Used to detect changes when block entity data is modified externally (e.g., via commands).
     * Key format: "world:x:y:z"
     */
    private final Map<String, ShulkerTrim> lastKnownTrims = new ConcurrentHashMap<>();

    private int changeDetectionTaskId = -1;

    public TrimSyncNetwork(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getLogger().info("Registered trim sync channel: " + CHANNEL);

        // Start periodic change detection task (runs every second)
        changeDetectionTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::checkForChanges, 20L, 20L);
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL);

        if (changeDetectionTaskId != -1) {
            Bukkit.getScheduler().cancelTask(changeDetectionTaskId);
            changeDetectionTaskId = -1;
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        // We don't expect any messages from clients, but this is required by the interface
    }

    /**
     * Send trim data for a specific block to all nearby players.
     */
    public void sendTrimSync(Location location, ShulkerTrim trim) {
        byte[] data = createTrimPacket(location, trim);
        if (data == null) return;

        // Send to all players in the same world who can see this block
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) < 256) {
                sendToPlayer(player, data);
            }
        }
    }

    /**
     * Send trim data for a specific block to a specific player.
     */
    public void sendTrimSync(Player player, Location location, ShulkerTrim trim) {
        byte[] data = createTrimPacket(location, trim);
        if (data != null) {
            sendToPlayer(player, data);
        }
    }

    /**
     * Sync all trimmed shulker boxes in a chunk to a player.
     * Also updates the last known state tracking.
     */
    public void syncChunkToPlayer(Player player, Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof ShulkerBox shulkerBox) {
                Location loc = state.getLocation();
                ShulkerTrim trim = ShulkerTrimStorage.readTrimFromBlock(shulkerBox);
                if (trim != null) {
                    sendTrimSync(player, loc, trim);
                    // Track this as the last known state
                    lastKnownTrims.put(getLocationKey(loc), trim);
                }
            }
        }
    }

    /**
     * Sync all loaded trimmed shulker boxes to a player.
     */
    public void syncAllToPlayer(Player player) {
        for (Chunk chunk : player.getWorld().getLoadedChunks()) {
            syncChunkToPlayer(player, chunk);
        }
    }

    /**
     * Periodically check for changes to shulker box trim data.
     * This handles external modifications (e.g., /data merge commands).
     */
    private void checkForChanges() {
        // Check each world's loaded chunks
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                checkChunkForChanges(chunk);
            }
        }
    }

    /**
     * Check a chunk for shulker box trim changes and broadcast updates to nearby players.
     * Also cleans up stale entries from lastKnownTrims when shulkers are removed.
     */
    private void checkChunkForChanges(Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // Track which locations in this chunk still have shulker boxes
        var existingShulkerLocations = new java.util.HashSet<String>();

        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof ShulkerBox shulkerBox) {
                Location loc = state.getLocation();
                String key = getLocationKey(loc);
                existingShulkerLocations.add(key);

                ShulkerTrim currentTrim = ShulkerTrimStorage.readTrimFromBlock(shulkerBox);
                ShulkerTrim lastTrim = lastKnownTrims.get(key);

                // Check if trim has changed
                if (!Objects.equals(currentTrim, lastTrim)) {
                    // Trim changed - broadcast update to all nearby players
                    if (currentTrim != null) {
                        sendTrimSync(loc, currentTrim);
                        lastKnownTrims.put(key, currentTrim);
                    } else {
                        // Trim removed - broadcast null trim packet
                        sendTrimRemoval(loc);
                        lastKnownTrims.remove(key);
                    }
                }
            }
        }

        // Clean up stale entries: remove lastKnownTrims entries for shulkers that no longer exist
        // This handles cases like /setblock air, explosions, or pistons removing shulkers
        lastKnownTrims.keySet().removeIf(key -> {
            // Only check keys in this chunk's world
            if (!key.startsWith(worldName + ":")) {
                return false;
            }

            // Parse coordinates from key (format: "world:x:y:z")
            String[] parts = key.split(":");
            if (parts.length != 4) {
                return false;
            }

            try {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);

                // Check if this location is in the current chunk
                int locChunkX = x >> 4;
                int locChunkZ = z >> 4;

                if (locChunkX == chunkX && locChunkZ == chunkZ) {
                    // This key is in our chunk - remove it if no shulker exists there anymore
                    return !existingShulkerLocations.contains(key);
                }
            } catch (NumberFormatException e) {
                // Invalid key format - leave it alone
            }

            return false;
        });
    }

    /**
     * Send a "trim removed" packet for a specific block.
     */
    public void sendTrimRemoval(Player player, Location location) {
        byte[] data = createTrimPacket(location, null);
        if (data != null) {
            sendToPlayer(player, data);
        }
    }

    /**
     * Send a "trim removed" packet to all nearby players.
     */
    public void sendTrimRemoval(Location location) {
        byte[] data = createTrimPacket(location, null);
        if (data == null) return;

        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) < 256) {
                sendToPlayer(player, data);
            }
        }
    }

    private String getLocationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private byte[] createTrimPacket(Location location, ShulkerTrim trim) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            // Write position
            dos.writeInt(location.getBlockX());
            dos.writeInt(location.getBlockY());
            dos.writeInt(location.getBlockZ());

            // Write trim data
            dos.writeBoolean(trim != null);
            if (trim != null) {
                writeUTF(dos, trim.pattern());
                writeUTF(dos, trim.material());
            }

            return baos.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create trim sync packet", e);
            return null;
        }
    }

    private void writeUTF(DataOutputStream dos, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }

    private void sendToPlayer(Player player, byte[] data) {
        if (player.getListeningPluginChannels().contains(CHANNEL)) {
            player.sendPluginMessage(plugin, CHANNEL, data);
        }
    }
}
