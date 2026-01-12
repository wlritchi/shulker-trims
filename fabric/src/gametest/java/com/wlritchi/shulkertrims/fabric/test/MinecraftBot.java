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
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple Minecraft bot client using MCProtocolLib for testing multi-player scenarios.
 * This bot can connect to a server, receive items, and place blocks.
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
        } else if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            LOGGER.debug("Bot '{}' inventory slot {} updated", username, slotPacket.getSlot());
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
        LOGGER.info("Bot '{}' sending position ({}, {}, {}) yaw={} pitch={}", username, x, y, z, yaw, pitch);
        // Args: onGround, horizontalCollision, x, y, z, yaw, pitch
        session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z, yaw, pitch));
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
