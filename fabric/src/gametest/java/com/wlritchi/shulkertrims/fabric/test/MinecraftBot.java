package com.wlritchi.shulkertrims.fabric.test;

import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A simple Minecraft bot client using MCProtocolLib for testing multi-player scenarios.
 * This bot can connect to a server, receive items, and place blocks.
 *
 * <p>This bot properly handles:
 * <ul>
 *   <li>Teleport confirmations (required for the server to accept subsequent packets)</li>
 *   <li>Inventory tracking (to know what items we have)</li>
 *   <li>Position tracking (to confirm we're at the right location)</li>
 * </ul>
 */
public class MinecraftBot implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftBot.class);

    private final String username;
    private final String host;
    private final int port;

    private ClientSession session;
    private final AtomicBoolean loggedIn = new AtomicBoolean(false);
    private final AtomicBoolean disconnected = new AtomicBoolean(false);
    private final AtomicInteger actionSequence = new AtomicInteger(0);
    private final CompletableFuture<Void> loginFuture = new CompletableFuture<>();

    private volatile String disconnectReason = null;

    // Position tracking
    private final AtomicReference<Double> posX = new AtomicReference<>(0.0);
    private final AtomicReference<Double> posY = new AtomicReference<>(0.0);
    private final AtomicReference<Double> posZ = new AtomicReference<>(0.0);
    private final AtomicReference<Float> yaw = new AtomicReference<>(0.0f);
    private final AtomicReference<Float> pitch = new AtomicReference<>(0.0f);

    // Inventory tracking (slot -> item)
    // Player inventory uses container ID 0, slots 0-8 are hotbar (mapped from protocol slots 36-44)
    private final ConcurrentHashMap<Integer, ItemStack> inventory = new ConcurrentHashMap<>();
    private final AtomicInteger selectedHotbarSlot = new AtomicInteger(0);

    public MinecraftBot(String username, String host, int port) {
        this.username = username;
        this.host = host;
        this.port = port;
    }

    /**
     * Connect to the server. Returns a future that completes when logged in.
     */
    public CompletableFuture<Void> connect() {
        LOGGER.info("Bot '{}' connecting to {}:{}", username, host, port);

        MinecraftProtocol protocol = new MinecraftProtocol(username);

        session = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress(host, port))
                .setProtocol(protocol)
                .create();

        session.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                handlePacket(packet);
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                disconnected.set(true);
                disconnectReason = event.getReason() != null ? event.getReason().toString() : "Unknown";
                LOGGER.info("Bot '{}' disconnected: {}", username, disconnectReason);

                if (!loginFuture.isDone()) {
                    loginFuture.completeExceptionally(
                            new RuntimeException("Disconnected before login: " + disconnectReason));
                }
            }
        });

        session.connect();
        return loginFuture;
    }

    private void handlePacket(Packet packet) {
        if (packet instanceof ClientboundLoginPacket) {
            LOGGER.info("Bot '{}' received login packet - now in game", username);
            loggedIn.set(true);
            loginFuture.complete(null);
        } else if (packet instanceof ClientboundPlayerPositionPacket positionPacket) {
            // Server is teleporting us - we MUST confirm or the server ignores all our packets
            handleTeleport(positionPacket);
        } else if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            handleSlotUpdate(slotPacket);
        } else if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
            handleContainerContent(contentPacket);
        }
    }

    /**
     * Handle server teleport. The server sends this when we're teleported via RCON or spawn.
     * We MUST respond with ServerboundAcceptTeleportationPacket or the server ignores all
     * subsequent packets from us.
     */
    private void handleTeleport(ClientboundPlayerPositionPacket packet) {
        int teleportId = packet.getId();

        // Extract position from Vector3d
        double x = packet.getPosition().getX();
        double y = packet.getPosition().getY();
        double z = packet.getPosition().getZ();
        float newYaw = packet.getYRot();
        float newPitch = packet.getXRot();

        // Update our tracked position
        // The position packet can have relative flags, but for simplicity we treat as absolute
        posX.set(x);
        posY.set(y);
        posZ.set(z);
        yaw.set(newYaw);
        pitch.set(newPitch);

        LOGGER.info("Bot '{}' teleported to ({}, {}, {}) yaw={} pitch={} teleportId={}",
                username, x, y, z, newYaw, newPitch, teleportId);

        // Send confirmation - this is CRITICAL for the server to accept our packets
        session.send(new ServerboundAcceptTeleportationPacket(teleportId));
        LOGGER.debug("Bot '{}' confirmed teleport {}", username, teleportId);

        // Also send position confirmation as vanilla client does
        session.send(new ServerboundMovePlayerPosRotPacket(
                true, false, // onGround, horizontalCollision
                x, y, z, newYaw, newPitch));
    }

    /**
     * Handle single slot inventory update.
     */
    private void handleSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        // Container ID 0 is the player inventory
        // Container ID -1 is the cursor
        // Container ID -2 is the player inventory (alternative)
        if (packet.getContainerId() == 0 || packet.getContainerId() == -2) {
            int slot = packet.getSlot();
            ItemStack item = packet.getItem();
            if (item != null && item.getAmount() > 0) {
                inventory.put(slot, item);
                LOGGER.info("Bot '{}' inventory slot {} set to {} x{}",
                        username, slot, item.getId(), item.getAmount());
            } else {
                inventory.remove(slot);
                LOGGER.debug("Bot '{}' inventory slot {} cleared", username, slot);
            }
        }
    }

    /**
     * Handle full container content update.
     */
    private void handleContainerContent(ClientboundContainerSetContentPacket packet) {
        // Container ID 0 is the player inventory
        if (packet.getContainerId() == 0) {
            inventory.clear();
            ItemStack[] items = packet.getItems();
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && items[i].getAmount() > 0) {
                    inventory.put(i, items[i]);
                }
            }
            LOGGER.info("Bot '{}' received full inventory update ({} non-empty slots)",
                    username, inventory.size());
        }
    }

    /**
     * Select a hotbar slot (0-8).
     */
    public void selectHotbarSlot(int slot) {
        if (!loggedIn.get()) {
            throw new IllegalStateException("Not logged in");
        }
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("Hotbar slot must be 0-8");
        }
        selectedHotbarSlot.set(slot);
        LOGGER.info("Bot '{}' selecting hotbar slot {}", username, slot);
        session.send(new ServerboundSetCarriedItemPacket(slot));
    }

    /**
     * Send position and rotation to the server.
     * This is necessary before interacting with blocks to confirm client position.
     */
    public void sendPosition(double x, double y, double z, float yaw, float pitch) {
        if (!loggedIn.get()) {
            throw new IllegalStateException("Not logged in");
        }
        // Update tracked position
        posX.set(x);
        posY.set(y);
        posZ.set(z);
        this.yaw.set(yaw);
        this.pitch.set(pitch);

        LOGGER.info("Bot '{}' sending position ({}, {}, {}) yaw={} pitch={}", username, x, y, z, yaw, pitch);
        // Args: onGround, horizontalCollision, x, y, z, yaw, pitch
        session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z, yaw, pitch));
    }

    /**
     * Get the item in the currently selected hotbar slot.
     * In player inventory, hotbar slots 0-8 are mapped to inventory slots 36-44.
     */
    public ItemStack getHeldItem() {
        int hotbarSlot = selectedHotbarSlot.get();
        int inventorySlot = 36 + hotbarSlot;
        return inventory.get(inventorySlot);
    }

    /**
     * Check if the bot has an item in the specified hotbar slot.
     */
    public boolean hasItemInHotbar(int slot) {
        int inventorySlot = 36 + slot;
        ItemStack item = inventory.get(inventorySlot);
        return item != null && item.getAmount() > 0;
    }

    /**
     * Get current tracked position.
     */
    public double[] getPosition() {
        return new double[] { posX.get(), posY.get(), posZ.get() };
    }

    /**
     * Place a block at the given position by clicking on the specified face of an adjacent block.
     *
     * @param targetBlockPos The position of the block to click on
     * @param face           The face of that block to click
     */
    public void placeBlock(Vector3i targetBlockPos, Direction face) {
        if (!loggedIn.get()) {
            throw new IllegalStateException("Not logged in");
        }

        // Log current state for debugging
        ItemStack heldItem = getHeldItem();
        double[] pos = getPosition();
        LOGGER.info("Bot '{}' attempting block placement:", username);
        LOGGER.info("  Position: ({}, {}, {})", pos[0], pos[1], pos[2]);
        LOGGER.info("  Target block: {} face {}", targetBlockPos, face);
        LOGGER.info("  Held item (slot {}): {}",
                selectedHotbarSlot.get(),
                heldItem != null ? heldItem.getId() + " x" + heldItem.getAmount() : "empty");
        LOGGER.info("  Inventory size: {} slots", inventory.size());

        int seq = actionSequence.incrementAndGet();
        LOGGER.info("Bot '{}' placing block at {} face {} (seq={})", username, targetBlockPos, face, seq);

        // Send the use item on packet (block placement)
        ServerboundUseItemOnPacket usePacket = new ServerboundUseItemOnPacket(
                targetBlockPos,
                face,
                Hand.MAIN_HAND,
                0.5f, 0.5f, 0.5f,  // Center of the face
                false,             // Not inside block
                false,             // Not hitting world border
                seq
        );
        session.send(usePacket);

        // Also send a swing animation (servers may require this)
        ServerboundSwingPacket swingPacket = new ServerboundSwingPacket(Hand.MAIN_HAND);
        session.send(swingPacket);
    }

    /**
     * Check if the bot is connected and logged in.
     */
    public boolean isLoggedIn() {
        return loggedIn.get() && !disconnected.get();
    }

    /**
     * Check if the bot has been disconnected.
     */
    public boolean isDisconnected() {
        return disconnected.get();
    }

    /**
     * Get the disconnect reason if disconnected.
     */
    public String getDisconnectReason() {
        return disconnectReason;
    }

    /**
     * Wait for the bot to be logged in.
     */
    public void waitForLogin(long timeout, TimeUnit unit) throws Exception {
        loginFuture.get(timeout, unit);
    }

    /**
     * Disconnect the bot from the server.
     */
    public void disconnect() {
        if (session != null && session.isConnected()) {
            LOGGER.info("Bot '{}' disconnecting", username);
            session.disconnect("Test complete");
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    public String getUsername() {
        return username;
    }
}
