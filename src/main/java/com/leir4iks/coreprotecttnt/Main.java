package com.leir4iks.coreprotecttnt;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.leir4iks.coreprotecttnt.listeners.ExplosionListener;
import com.leir4iks.coreprotecttnt.listeners.FireListener;
import com.leir4iks.coreprotecttnt.listeners.HangingListener;
import com.leir4iks.coreprotecttnt.listeners.TrackingListener;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Main extends JavaPlugin {
   private final Cache<Object, String> probablyCache = CacheBuilder.newBuilder()
           .expireAfterAccess(1L, TimeUnit.HOURS)
           .concurrencyLevel(4)
           .maximumSize(50000L)
           .build();

   private final Cache<UUID, Boolean> processedEntities = CacheBuilder.newBuilder()
           .expireAfterWrite(2, TimeUnit.SECONDS)
           .build();

   private CoreProtectAPI api;
   private UpdateChecker updateChecker;

   @Override
   public void onEnable() {
      saveDefaultConfig();

      int pluginId = 26755;
      new Metrics(this, pluginId);

      Plugin depend = Bukkit.getPluginManager().getPlugin("CoreProtect");
      if (depend instanceof CoreProtect) {
         CoreProtectAPI coreProtectAPI = ((CoreProtect) depend).getAPI();
         if (coreProtectAPI.APIVersion() >= 10) {
            this.api = coreProtectAPI;
            registerListeners();
            getLogger().info("Successfully hooked into CoreProtect API v" + coreProtectAPI.APIVersion());
         } else {
            getLogger().severe("CoreProtect API version 10 or higher is required. Disabling plugin.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
         }
      } else {
         getLogger().severe("CoreProtect not found. Disabling plugin.");
         this.getServer().getPluginManager().disablePlugin(this);
         return;
      }

      this.updateChecker = new UpdateChecker(this);
      updateChecker.check();

      Objects.requireNonNull(getCommand("cptnt")).setExecutor(new UpdateCommand(updateChecker));
   }

   private void registerListeners() {
      Bukkit.getPluginManager().registerEvents(new ExplosionListener(this), this);
      Bukkit.getPluginManager().registerEvents(new FireListener(this), this);
      Bukkit.getPluginManager().registerEvents(new HangingListener(this), this);
      Bukkit.getPluginManager().registerEvents(new TrackingListener(this), this);
   }

   public CoreProtectAPI getApi() {
      return api;
   }

   public Cache<Object, String> getCache() {
      return probablyCache;
   }

   public Cache<UUID, Boolean> getProcessedEntities() {
      return processedEntities;
   }
}