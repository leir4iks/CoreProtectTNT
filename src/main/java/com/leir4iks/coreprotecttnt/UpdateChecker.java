package com.leir4iks.coreprotecttnt;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.leir4iks.coreprotecttnt.config.Config;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {

    private final Main plugin;
    private final String currentVersion;
    private final Gson gson = new Gson();

    private static final String API_URL = "https://update.cookiev.cv/update/check";

    private UpdateInfo latestUpdateInfo;
    private boolean updateAvailable = false;

    public UpdateChecker(Main plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void check() {
        if (!plugin.getConfigManager().get().general.updateChecker.enabled) {
            return;
        }
        performCheck(plugin.getServer().getConsoleSender(), false);
    }

    public void checkAndNotify(CommandSender sender) {
        sendMessage(sender, plugin.getConfigManager().get().localization.messages.updateChecking);
        performCheck(sender, true);
    }

    private void performCheck(CommandSender notifier, boolean verbose) {
        fetchUpdateInfo().thenAcceptAsync(info -> {
            if (info != null && info.latestVersion() != null && isNewer(info.latestVersion(), this.currentVersion)) {
                this.latestUpdateInfo = info;
                this.updateAvailable = true;
                notifyUpdateAvailable(notifier);
                if (plugin.getConfigManager().get().general.updateChecker.autoDownload) {
                    downloadUpdate(notifier);
                }
            } else {
                this.updateAvailable = false;
                if (verbose) {
                    sendMessage(notifier, "&aYou are running the latest version.");
                }
            }
        }).exceptionally(ex -> {
            if (verbose) {
                sendMessage(notifier, plugin.getConfigManager().get().localization.messages.updateFailed);
            }
            plugin.getLogger().warning("Update check failed: " + ex.getMessage());
            return null;
        });
    }

    private CompletableFuture<UpdateInfo> fetchUpdateInfo() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "CoreProtectTNT-Updater");

                if (connection.getResponseCode() != 200) {
                    throw new IOException("Failed to check for updates (HTTP " + connection.getResponseCode() + ").");
                }

                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    return gson.fromJson(reader, UpdateInfo.class);
                }
            } catch (IOException | JsonSyntaxException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void downloadUpdate(CommandSender notifier) {
        Main.getScheduler().runTaskAsynchronously(() -> {
            try {
                File updateFolder = plugin.getServer().getUpdateFolderFile();
                if (!updateFolder.exists() && !updateFolder.mkdirs()) {
                    throw new IOException("Could not create update folder.");
                }

                File destination = new File(updateFolder, plugin.getDescription().getName() + ".jar");
                URL downloadUrl = new URL(latestUpdateInfo.downloadUrl());

                HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "CoreProtectTNT-Updater");

                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                sendMessage(notifier, plugin.getConfigManager().get().localization.messages.updateDownloaded);
            } catch (IOException e) {
                sendMessage(notifier, "&cFailed to download the update.");
                plugin.getLogger().warning("Update download failed: " + e.getMessage());
            }
        });
    }

    private void notifyUpdateAvailable(CommandSender notifier) {
        Config.Messages msgs = plugin.getConfigManager().get().localization.messages;
        sendMessage(notifier, msgs.updateAvailable);
        notifier.sendMessage(ChatColor.YELLOW + "  Current: " + ChatColor.RED + this.currentVersion + ChatColor.YELLOW + " -> Latest: " + ChatColor.GREEN + latestUpdateInfo.latestVersion());
        notifier.sendMessage(ChatColor.AQUA + "  Changelog:");
        if (latestUpdateInfo.changelog() != null) {
            for (String line : latestUpdateInfo.changelog()) {
                notifier.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + line);
            }
        }
        if (!plugin.getConfigManager().get().general.updateChecker.autoDownload) {
            notifier.sendMessage(ChatColor.YELLOW + "  Download it from: " + latestUpdateInfo.downloadUrl());
        }
    }

    private void sendMessage(CommandSender sender, String message) {
        String prefix = plugin.getConfigManager().get().localization.messages.prefix;
        String fullMessage = ChatColor.translateAlternateColorCodes('&', prefix + message);
        if (sender instanceof Player) {
            sender.sendMessage(fullMessage);
        } else {
            plugin.getLogger().info(ChatColor.stripColor(fullMessage));
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