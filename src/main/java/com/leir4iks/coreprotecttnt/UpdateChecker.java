package com.leir4iks.coreprotecttnt;

import org.bukkit.plugin.java.JavaPlugin;

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final String currentVersion;
    private static final String API_URL = "http://212.80.7.202:20564/update/coreprotecttnt";

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void check() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getLogger().info("Checking for updates...");
            plugin.getLogger().info("Update check finished.");
        });
    }

    public void checkAndNotify() {
    }
}