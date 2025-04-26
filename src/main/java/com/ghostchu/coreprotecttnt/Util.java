package com.ghostchu.coreprotecttnt;

import java.util.Iterator;
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
         Iterator var4 = location.getWorld().getNearbyEntities(location, 15.0D, 15.0D, 15.0D, (entity) -> {
            return entity instanceof Player;
         }).iterator();

         while(var4.hasNext()) {
            Entity around = (Entity)var4.next();
            around.sendMessage(msg);
         }

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
