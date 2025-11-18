package com.leir4iks.coreprotecttnt;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Locale;

public class Util {
   private static final int MAX_CAUSE_LENGTH = 255;

   public static void broadcastNearPlayers(Location location, String message) {
      if (message != null && !message.isEmpty()) {
         String msg = ChatColor.translateAlternateColorCodes('&', message);
         // Используем radius 15 для оповещения
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
      StringBuilder newCause = new StringBuilder();
      newCause.append("#")
              .append(entity.getType().name().toLowerCase(Locale.ROOT))
              .append("-")
              .append(previousCause);

      if (newCause.length() > MAX_CAUSE_LENGTH) {
         return newCause.substring(0, MAX_CAUSE_LENGTH - 3) + "...";
      }

      return newCause.toString();
   }

   public static String getRootCause(String cause) {
      if (cause == null) {
         return null;
      }
      int lastDash = cause.lastIndexOf('-');
      if (lastDash != -1 && lastDash < cause.length() - 1) {
         return cause.substring(lastDash + 1);
      }
      return cause;
   }

   public static String getEntityExplosionCause(Entity entity, Main plugin) {
      if (entity == null) return null;

      String track = plugin.getProjectileCache().getIfPresent(entity.getUniqueId());
      if (track != null) {
         return track;
      }

      if (entity instanceof Creeper creeper && creeper.getTarget() != null) {
         return creeper.getTarget().getName();
      }

      if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent event) {
         Entity damager = event.getDamager();
         String damagerTrack = plugin.getProjectileCache().getIfPresent(damager.getUniqueId());
         if (damagerTrack != null) {
            return createChainedCause(damager, damagerTrack);
         } else {
            if (damager instanceof Player) {
               return damager.getName();
            }
            return damager.getType().name().toLowerCase(Locale.ROOT);
         }
      }
      return null;
   }
}