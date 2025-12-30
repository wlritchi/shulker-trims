package com.wlritchi.shulkertrims.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

public class ShulkerTrimsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Shulker Trims enabling...");

        // Register smithing recipes
        ShulkerTrimsRecipes.register(this);

        // Register event listeners for NBT handling
        getServer().getPluginManager().registerEvents(new ShulkerTrimsListener(this), this);

        getLogger().info("Shulker Trims enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shulker Trims disabled");
    }
}
