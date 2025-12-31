package com.wlritchi.shulkertrims.fabric.client;

import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import net.fabricmc.api.ClientModInitializer;

public class ShulkerTrimsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ShulkerTrimsMod.LOGGER.info("Shulker Trims client initializing...");

        // Register network handler for Paper server sync
        TrimSyncNetworkClient.register();

        ShulkerTrimsMod.LOGGER.info("Shulker Trims client initialized");
    }
}
