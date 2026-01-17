package com.leir4iks.coreprotecttnt;

import com.leir4iks.coreprotecttnt.config.Config;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Locale;

public class Util {
   private static final int MAX_CAUSE_LENGTH = 255;

   @SuppressWarnings("deprecation")
   public static void broadcastNearPlayers(Location location, String message) {
      if (message != null && !message.isEmpty()) {
         String msg = ChatColor.translateAlternateColorCodes('&', message);
         location.getWorld().getNearbyPlayers(location, 15.0D)
                 .forEach(player -> player.sendMessage(msg));
      }
   }

   public static String getTranslatedName(Main plugin, String key) {
      key = key.replace("-", "_");
      return plugin.getConfigManager().get().localization.causes.getOrDefault(key, key);
   }

   public static String createChainedCause(Main plugin, String currentType, String previousCause) {
      Config config = plugin.getConfigManager().get();
      String prefix = config.formatting.logPrefix;
      String separator = config.formatting.causeSeparator;
      String translatedType = getTranslatedName(plugin, currentType);

      StringBuilder newCause = new StringBuilder(prefix);
      newCause.append(translatedType);

      if (previousCause != null && !previousCause.isEmpty()) {
         newCause.append(separator);
         if (previousCause.startsWith(prefix)) {
            newCause.append(previousCause, prefix.length(), previousCause.length());
         } else {
            newCause.append(previousCause);
         }
      }

      if (newCause.length() > MAX_CAUSE_LENGTH) {
         return newCause.substring(0, MAX_CAUSE_LENGTH - 3) + "...";
      }

      return newCause.toString();
   }

   public static String createChainedCause(Main plugin, Entity entity, String previousCause) {
      return createChainedCause(plugin, entity.getType().name().toLowerCase(Locale.ROOT), previousCause);
   }

   public static String getRootCause(Main plugin, String cause) {
      if (cause == null) {
         return null;
      }
      String separator = plugin.getConfigManager().get().formatting.causeSeparator;
      int lastSep = cause.lastIndexOf(separator);
      if (lastSep != -1 && lastSep < cause.length() - separator.length()) {
         return cause.substring(lastSep + separator.length());
      }
      return cause;
   }

   public static String getEntityExplosionCause(Entity entity, Main plugin) {
      if (entity == null) return null;

      String track = plugin.getProjectileCache().getIfPresent(entity.getUniqueId());
      if (track != null) {
         return track;
      }

      if (entity instanceof Creeper creeper && creeper.getTarget() instanceof Player target) {
         return createChainedCause(plugin, creeper, target.getName());
      }

      String aggro = plugin.getEntityAggroCache().getIfPresent(entity.getUniqueId());
      if (aggro != null) {
         return aggro;
      }

      if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent event) {
         Entity damager = event.getDamager();
         String damagerTrack = plugin.getProjectileCache().getIfPresent(damager.getUniqueId());
         if (damagerTrack != null) {
            return createChainedCause(plugin, damager, damagerTrack);
         } else {
            if (damager instanceof Player) {
               return damager.getName();
            }
            return damager.getType().name().toLowerCase(Locale.ROOT);
         }
      }
      return null;
   }

   public static double getExplosionRadius(EntityType type, Entity entity) {
      return switch (type) {
         case CREEPER -> (entity instanceof Creeper creeper && creeper.isPowered()) ? 6.0 : 3.0;
         case END_CRYSTAL, TNT_MINECART -> 6.0;
         case WITHER, WITHER_SKULL, FIREBALL, SMALL_FIREBALL, DRAGON_FIREBALL, WIND_CHARGE, BREEZE_WIND_CHARGE -> 2.0;
         default -> 4.0;
      };
   }

   public static String addPrefixIfNeeded(Main plugin, String cause) {
      String prefix = plugin.getConfigManager().get().formatting.logPrefix;
      return cause.startsWith(prefix) ? cause : prefix + cause;
   }
}