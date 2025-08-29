package com.leir4iks.coreprotecttnt;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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
        Main.getScheduler().runTaskAsynchronously(() -> {
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
                            downloadUpdate(notifier);
                        }
                    } else {
                        this.updateAvailable = false;
                        notifyResult(notifier, ChatColor.GREEN + "You are running the latest version.");
                    }
                }

            } catch (IOException | JsonSyntaxException e) {
                notifyResult(notifier, ChatColor.RED + "An error occurred while checking for updates.");
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
            }
        });
    }

    private void downloadUpdate(CommandSender notifier) {
        Main.getScheduler().runTaskAsynchronously(() -> {
            try {
                File updateFolder = new File(plugin.getServer().getUpdateFolderFile(), "");
                if (!updateFolder.exists()) {
                    updateFolder.mkdirs();
                }

                File destination = new File(updateFolder, plugin.getDescription().getName() + ".jar");
                URL downloadUrl = new URL(latestUpdateInfo.downloadUrl());

                HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                notifyResult(notifier, ChatColor.GREEN + "Update downloaded successfully. Please restart the server to apply it.");

            } catch (IOException e) {
                notifyResult(notifier, ChatColor.RED + "Failed to download the update.");
                plugin.getLogger().warning("Update download failed: " + e.getMessage());
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
        if (!plugin.getConfig().getBoolean("update-checker.auto-download", true)) {
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