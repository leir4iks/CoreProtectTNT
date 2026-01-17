package com.leir4iks.coreprotecttnt;

import com.leir4iks.coreprotecttnt.config.Config;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class UpdateCommand implements CommandExecutor {

    private final Main plugin;
    private final UpdateChecker updateChecker;

    public UpdateCommand(Main plugin) {
        this.plugin = plugin;
        this.updateChecker = plugin.getUpdateChecker();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Config.Messages msgs = plugin.getConfigManager().get().localization.messages;

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("update")) {
                if (!sender.hasPermission("coreprotecttnt.update")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msgs.noPermission));
                    return true;
                }
                updateChecker.checkAndNotify(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("version")) {
                if (!sender.hasPermission("coreprotecttnt.version")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msgs.noPermission));
                    return true;
                }

                sender.sendMessage(ChatColor.GOLD + "--- CoreProtectTNT Version ---");
                sender.sendMessage(ChatColor.YELLOW + "Current version: " + ChatColor.WHITE + updateChecker.getCurrentVersion());
                if (updateChecker.isUpdateAvailable()) {
                    sender.sendMessage(ChatColor.YELLOW + "Latest version: " + ChatColor.GREEN + updateChecker.getLatestUpdateInfo().latestVersion());
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msgs.updateAvailable));
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Latest version: " + ChatColor.GREEN + updateChecker.getCurrentVersion());
                    sender.sendMessage(ChatColor.GREEN + "You are running the latest version.");
                }
                return true;
            }
        }

        sender.sendMessage(ChatColor.RED + "Usage: /cptnt <update|version>");
        return true;
    }
}