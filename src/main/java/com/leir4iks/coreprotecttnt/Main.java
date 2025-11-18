package com.leir4iks.coreprotecttnt;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.leir4iks.coreprotecttnt.listeners.*;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Main extends JavaPlugin {
   public record BlockKey(UUID worldId, int x, int y, int z) {
      public static BlockKey from(Location loc) {
         return new BlockKey(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
      }
      public static BlockKey from(UUID worldId, int x, int y, int z) {
         return new BlockKey(worldId, x, y, z);
      }
   }

   private final Cache<BlockKey, String> blockPlaceCache = CacheBuilder.newBuilder()
           .expireAfterWrite(6, TimeUnit.HOURS).build();
   private final Cache<UUID, String> entityAggroCache = CacheBuilder.newBuilder()
           .expireAfterWrite(15, TimeUnit.MINUTES).build();
   private final Cache<UUID, String> projectileCache = CacheBuilder.newBuilder()
           .expireAfterWrite(1, TimeUnit.MINUTES).build();
   private final Cache<UUID, Boolean> processedEntities = CacheBuilder.newBuilder()
           .expireAfterWrite(2, TimeUnit.SECONDS).build();

   private CoreProtectAPI api;
   private UpdateChecker updateChecker;
   private static TaskScheduler scheduler;

   @Override
   public void onEnable() {
      saveDefaultConfig();
      scheduler = UniversalScheduler.getScheduler(this);

      if (!setupCoreProtect()) {
         return;
      }

      registerListeners();
      this.updateChecker = new UpdateChecker(this);
      updateChecker.check();
      Objects.requireNonNull(getCommand("cptnt")).setExecutor(new UpdateCommand(updateChecker));

      new Metrics(this, 26755);

      getLogger().info("CoreProtectTNT has been successfully enabled.");
   }

   private boolean setupCoreProtect() {
      Plugin depend = Bukkit.getPluginManager().getPlugin("CoreProtect");
      if (!(depend instanceof CoreProtect)) {
         getLogger().log(Level.SEVERE, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
         getLogger().log(Level.SEVERE, "CoreProtect not found!");
         getLogger().log(Level.SEVERE, "CoreProtectTNT will not function without it!");
         getLogger().log(Level.SEVERE, "Please, download it from: https://github.com/PlayPro/CoreProtect/releases");
         getLogger().log(Level.SEVERE, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
         return false;
      }

      CoreProtectAPI coreProtectAPI = ((CoreProtect) depend).getAPI();
      if (coreProtectAPI.APIVersion() < 9) {
         getLogger().log(Level.SEVERE, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
         getLogger().log(Level.SEVERE, "Your version of CoreProtect is too old!");
         getLogger().log(Level.SEVERE, "CoreProtectTNT requires API version 9 or higher.");
         getLogger().log(Level.SEVERE, "Please update CoreProtect.");
         getLogger().log(Level.SEVERE, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
         return false;
      }

      this.api = coreProtectAPI;
      return true;
   }

   private void registerListeners() {
      Bukkit.getPluginManager().registerEvents(new ExplosionListener(this), this);
      Bukkit.getPluginManager().registerEvents(new FireListener(this), this);
      Bukkit.getPluginManager().registerEvents(new HangingListener(this), this);
      Bukkit.getPluginManager().registerEvents(new TrackingListener(this), this);
      Bukkit.getPluginManager().registerEvents(new FrameListener(this), this);
   }

   public CoreProtectAPI getApi() {
      return api;
   }

   public static TaskScheduler getScheduler() {
      return scheduler;
   }

   public Cache<BlockKey, String> getBlockPlaceCache() {
      return blockPlaceCache;
   }

   public Cache<UUID, String> getEntityAggroCache() {
      return entityAggroCache;
   }

   public Cache<UUID, String> getProjectileCache() {
      return projectileCache;
   }

   public Cache<UUID, Boolean> getProcessedEntities() {
      return processedEntities;
   }
}