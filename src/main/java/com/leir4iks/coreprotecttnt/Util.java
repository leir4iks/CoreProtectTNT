package com.leir4iks.coreprotecttnt;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class Util {
   public static void broadcastNearPlayers(Location location, String message) {
      if (message != null && !message.isEmpty()) {
         String msg = ChatColor.translateAlternateColorCodes('&', message);
         location.getWorld().getNearbyEntities(location, 15.0D, 15.0D, 15.0D, (entity) -> entity instanceof Player)
                 .forEach(entity -> entity.sendMessage(msg));
      }
   }

   public static ConfigurationSection bakeConfigSection(Configuration configuration, String path) {
      ConfigurationSection section = configuration.getConfigurationSection(path);
      if (section == null) {
         section = configuration.createSection(path);
         section.set("enable", true);
         section.set("disable-unknown", true);
         section.set("alert", String.valueOf(ChatColor.RED) + "Failed to read translation, configuration section missing!");
      }
      return section;
   }
}