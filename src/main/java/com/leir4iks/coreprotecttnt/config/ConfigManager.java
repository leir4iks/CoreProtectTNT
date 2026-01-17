package com.leir4iks.coreprotecttnt.config;

import com.leir4iks.coreprotecttnt.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Level;

public class ConfigManager {
    private final Main plugin;
    private Config config;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        updateConfig();
        plugin.reloadConfig();
        FileConfiguration file = plugin.getConfig();
        this.config = new Config();

        config.general.debug = file.getBoolean("general-settings.debug", false);
        config.general.updateChecker.enabled = file.getBoolean("general-settings.update-checker.enabled", true);
        config.general.updateChecker.autoDownload = file.getBoolean("general-settings.update-checker.auto-download", true);

        config.formatting.logPrefix = file.getString("formatting.log-prefix", "#");
        config.formatting.causeSeparator = file.getString("formatting.cause-separator", "-");

        config.modules.blockExplosions.enabled = file.getBoolean("modules.block-explosions.enabled", true);
        config.modules.blockExplosions.disableUnknown = file.getBoolean("modules.block-explosions.disable-unknown-sources", false);

        config.modules.entityExplosions.enabled = file.getBoolean("modules.entity-explosions.enabled", true);
        config.modules.entityExplosions.disableUnknown = file.getBoolean("modules.entity-explosions.disable-unknown-sources", false);

        config.modules.fire.enabled = file.getBoolean("modules.fire.enabled", true);
        config.modules.fire.disableUnknown = file.getBoolean("modules.fire.disable-unknown-sources", false);

        config.modules.itemFrames.enabled = file.getBoolean("modules.item-frames.enabled", true);
        config.modules.itemFrames.disableUnknown = file.getBoolean("modules.item-frames.disable-unknown-sources", false);
        config.modules.itemFrames.logPlacement = file.getBoolean("modules.item-frames.log-placement", true);
        config.modules.itemFrames.logRotation = file.getBoolean("modules.item-frames.log-rotation", true);
        config.modules.itemFrames.logContentChange = file.getBoolean("modules.item-frames.log-content-change", true);
        config.modules.itemFrames.logPistonDestruction = file.getBoolean("modules.item-frames.log-piston-destruction", true);
        config.modules.itemFrames.logWaterDestruction = file.getBoolean("modules.item-frames.log-water-destruction", true);
        config.modules.itemFrames.logPhysicsDestruction = file.getBoolean("modules.item-frames.log-physics-destruction", true);

        config.modules.hanging.enabled = file.getBoolean("modules.hanging.enabled", true);
        config.modules.hanging.disableUnknown = file.getBoolean("modules.hanging.disable-unknown-sources", false);

        config.localization.messages.prefix = file.getString("localization.messages.prefix", "&6[CoreProtectTNT] &e");
        config.localization.messages.updateChecking = file.getString("localization.messages.update-checking", "Checking for updates...");
        config.localization.messages.updateAvailable = file.getString("localization.messages.update-available", "A new version is available!");
        config.localization.messages.updateDownloaded = file.getString("localization.messages.update-downloaded", "&aUpdate downloaded. Restart to apply.");
        config.localization.messages.updateFailed = file.getString("localization.messages.update-failed", "&cUpdate check failed.");
        config.localization.messages.noPermission = file.getString("localization.messages.no-permission", "&cYou do not have permission.");
        config.localization.messages.unknownSourceAlert = file.getString("localization.messages.unknown-source-alert", "&cAction blocked: Unknown source.");

        ConfigurationSection causes = file.getConfigurationSection("localization.causes");
        if (causes != null) {
            for (String key : causes.getKeys(false)) {
                config.localization.causes.put(key.replace("-", "_"), causes.getString(key));
            }
        }
    }

    private void updateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        try (InputStream defConfigStream = plugin.getResource("config.yml")) {
            if (defConfigStream == null) return;

            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            boolean modified = false;

            Set<String> defKeys = defConfig.getKeys(true);
            for (String key : defKeys) {
                if (!currentConfig.contains(key)) {
                    currentConfig.set(key, defConfig.get(key));
                    modified = true;
                }
            }

            if (modified) {
                currentConfig.save(configFile);
                plugin.getLogger().info("Configuration file updated with new keys.");
            }
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Could not update config.yml", e);
        }
    }

    public Config get() {
        return config;
    }
}