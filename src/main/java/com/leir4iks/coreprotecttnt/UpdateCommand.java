package com.leir4iks.coreprotecttnt;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class UpdateCommand implements CommandExecutor {

    private final UpdateChecker updateChecker;

    public UpdateCommand(UpdateChecker updateChecker) {
        this.updateChecker = updateChecker;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("update")) {
                if (!sender.hasPermission("coreprotecttnt.update")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
                updateChecker.checkAndNotify(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("version")) {
                if (!sender.hasPermission("coreprotecttnt.version")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }

                sender.sendMessage(ChatColor.GOLD + "--- CoreProtectTNT Version ---");
                sender.sendMessage(ChatColor.YELLOW + "Current version: " + ChatColor.WHITE + updateChecker.getCurrentVersion());
                if (updateChecker.isUpdateAvailable()) {
                    sender.sendMessage(ChatColor.YELLOW + "Latest version: " + ChatColor.GREEN + updateChecker.getLatestUpdateInfo().latestVersion());
                    sender.sendMessage(ChatColor.AQUA + "An update is available! Run /cptnt update to see details.");
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