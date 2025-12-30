package com.wlritchi.shulkertrims.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShulkerTrimsMod implements ModInitializer {
    public static final String MOD_ID = "shulker_trims";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Shulker Trims initializing...");

        // Register recipes
        ShulkerTrimsRecipes.register();

        LOGGER.info("Shulker Trims initialized");
    }
}
