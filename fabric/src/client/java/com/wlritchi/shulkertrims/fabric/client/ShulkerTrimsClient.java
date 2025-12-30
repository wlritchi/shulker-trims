package com.wlritchi.shulkertrims.fabric.client;

import com.wlritchi.shulkertrims.fabric.ShulkerTrimsMod;
import net.fabricmc.api.ClientModInitializer;

public class ShulkerTrimsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ShulkerTrimsMod.LOGGER.info("Shulker Trims client initializing...");

        // Client-side rendering setup will go here
        // - Register trim texture atlas
        // - Set up item model overrides for trimmed shulkers

        ShulkerTrimsMod.LOGGER.info("Shulker Trims client initialized");
    }
}
