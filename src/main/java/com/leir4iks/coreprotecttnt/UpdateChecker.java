package com.leir4iks.coreprotecttnt;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final String currentVersion;
    private static final String API_URL = "http://212.80.7.202:20564/update/coreprotecttnt";
    private UpdateInfo latestUpdateInfo;
    private boolean updateAvailable = false;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void check() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }
        performCheck(plugin.getServer().getConsoleSender());
    }

    public void checkAndNotify(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Checking for updates...");
        performCheck(sender);
    }

    private void performCheck(CommandSender notifier) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() != 200) {
                    notifyResult(notifier, ChatColor.RED + "Failed to check for updates (HTTP " + connection.getResponseCode() + ").");
                    return;
                }

                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    UpdateInfo info = new Gson().fromJson(reader, UpdateInfo.class);
                    if (isNewer(info.latestVersion(), this.currentVersion)) {
                        this.latestUpdateInfo = info;
                        this.updateAvailable = true;
                        notifyUpdateAvailable(notifier);
                        if (plugin.getConfig().getBoolean("update-checker.auto-download", true)) {
                        }
                    } else {
                        notifyResult(notifier, ChatColor.GREEN + "You are running the latest version.");
                    }
                }

            } catch (IOException | JsonSyntaxException e) {
                notifyResult(notifier, ChatColor.RED + "An error occurred while checking for updates.");
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
            }
        });
    }

    private void notifyUpdateAvailable(CommandSender notifier) {
        notifier.sendMessage(ChatColor.GOLD + "A new version of CoreProtectTNT is available!");
        notifier.sendMessage(ChatColor.YELLOW + "Current: " + ChatColor.RED + this.currentVersion + ChatColor.YELLOW + " -> Latest: " + ChatColor.GREEN + latestUpdateInfo.latestVersion());
        notifier.sendMessage(ChatColor.AQUA + "Changelog:");
        for (String line : latestUpdateInfo.changelog()) {
            notifier.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + line);
        }
        if (plugin.getConfig().getBoolean("update-checker.auto-download", true)) {
            notifier.sendMessage(ChatColor.GREEN + "The new version will be downloaded automatically.");
            notifier.sendMessage(ChatColor.GREEN + "Please restart the server to apply the update.");
        } else {
            notifier.sendMessage(ChatColor.YELLOW + "Download it from: " + latestUpdateInfo.downloadUrl());
        }
    }

    private void notifyResult(CommandSender notifier, String message) {
        if (notifier instanceof org.bukkit.entity.Player) {
            notifier.sendMessage(message);
        } else {
            plugin.getLogger().info(ChatColor.stripColor(message));
        }
    }

    private boolean isNewer(String newVersion, String oldVersion) {
        return !newVersion.equalsIgnoreCase(oldVersion);
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public UpdateInfo getLatestUpdateInfo() {
        return latestUpdateInfo;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }
}