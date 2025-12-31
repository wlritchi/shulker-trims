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

    public TrimSyncNetwork(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getLogger().info("Registered trim sync channel: " + CHANNEL);
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL);
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
     */
    public void syncChunkToPlayer(Player player, Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof ShulkerBox shulkerBox) {
                ShulkerTrim trim = ShulkerTrimStorage.readTrimFromBlock(shulkerBox);
                if (trim != null) {
                    sendTrimSync(player, state.getLocation(), trim);
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
