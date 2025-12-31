package com.wlritchi.shulkertrims.fabric.client;

import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Handles receiving trim sync packets from Paper servers.
 *
 * The actual packet reception is handled by mixins on CustomPayloadS2CPacket,
 * because Fabric's networking API only works with Fabric servers for the handler callback.
 * However, we still use Fabric's API to register the channel, which tells the server
 * we want to receive packets on this channel.
 */
public class TrimSyncNetworkClient {

    public static void register() {
        // Register the payload type - this is needed for both encoding/decoding
        // AND for telling the server we accept this channel
        PayloadTypeRegistry.playS2C().register(TrimSyncPayload.ID, TrimSyncPayload.CODEC);

        // Register a receiver with Fabric API - this triggers channel registration with the server
        // The actual handling is done by our mixin, but this ensures the server knows we listen
        ClientPlayNetworking.registerGlobalReceiver(TrimSyncPayload.ID, (payload, context) -> {
            // This won't be called for Paper servers (mixin handles it),
            // but it WILL be called for Fabric servers, so handle both cases
            ShulkerTrimsMod.LOGGER.info("Received trim sync via Fabric API at ({}, {}, {})",
                payload.x(), payload.y(), payload.z());

            context.client().execute(() -> {
                var world = context.client().world;
                if (world == null) return;

                var pos = new net.minecraft.util.math.BlockPos(payload.x(), payload.y(), payload.z());
                var blockEntity = world.getBlockEntity(pos);

                if (blockEntity instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity &&
                    blockEntity instanceof com.wlritchi.shulkertrims.fabric.TrimmedShulkerBox trimmed) {
                    trimmed.shulkerTrims$setTrim(payload.trim());
                    ShulkerTrimsMod.LOGGER.info("Applied trim at {}: {}", pos, payload.trim());
                }
            });
        });

        ShulkerTrimsMod.LOGGER.info("Trim sync channel registered");
    }
}
