package com.leir4iks.coreprotecttnt;

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
        if (args.length > 0 && args[0].equalsIgnoreCase("update")) {
            if (!sender.hasPermission("coreprotecttnt.update")) {
                sender.sendMessage("You do not have permission to use this command.");
                return true;
            }
            sender.sendMessage("Checking for updates...");
            updateChecker.checkAndNotify();
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("version")) {
            if (!sender.hasPermission("coreprotecttnt.version")) {
                sender.sendMessage("You do not have permission to use this command.");
                return true;
            }
            sender.sendMessage("Version check logic is not implemented yet.");
            return true;
        }

        sender.sendMessage("Usage: /cptnt <update|version>");
        return true;
    }
}