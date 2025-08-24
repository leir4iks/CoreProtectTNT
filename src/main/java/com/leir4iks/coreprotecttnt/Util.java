package com.leir4iks.coreprotecttnt;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Locale;

public class Util {
   private static final int MAX_CAUSE_LENGTH = 256;

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

   public static String createChainedCause(Entity entity, String previousCause) {
      String newCause = "#" + entity.getType().name().toLowerCase(Locale.ROOT) + "-" + previousCause;

      if (newCause.length() > MAX_CAUSE_LENGTH) {
         return newCause.substring(0, MAX_CAUSE_LENGTH - 3) + "...";
      }

      return newCause;
   }

   public static String getRootCause(String cause) {
      if (cause == null) {
         return null;
      }
      if (cause.contains("-")) {
         String[] parts = cause.split("-");
         return parts[parts.length - 1];
      }
      return cause;
   }
}