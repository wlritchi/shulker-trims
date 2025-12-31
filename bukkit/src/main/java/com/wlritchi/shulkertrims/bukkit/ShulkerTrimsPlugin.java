package com.wlritchi.shulkertrims.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

public class ShulkerTrimsPlugin extends JavaPlugin {

    private TrimSyncNetwork trimSyncNetwork;

    @Override
    public void onEnable() {
        getLogger().info("Shulker Trims enabling...");

        // Register smithing recipes
        ShulkerTrimsRecipes.register(this);

        // Initialize trim sync networking (for Fabric client support)
        trimSyncNetwork = new TrimSyncNetwork(this);
        trimSyncNetwork.register();

        // Register event listeners for NBT handling
        getServer().getPluginManager().registerEvents(new ShulkerTrimsListener(this, trimSyncNetwork), this);

        getLogger().info("Shulker Trims enabled");
    }

    @Override
    public void onDisable() {
        if (trimSyncNetwork != null) {
            trimSyncNetwork.unregister();
        }
        getLogger().info("Shulker Trims disabled");
    }

    public TrimSyncNetwork getTrimSyncNetwork() {
        return trimSyncNetwork;
    }
}
