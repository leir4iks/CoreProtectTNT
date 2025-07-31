package com.leir4iks.coreprotecttnt;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.leir4iks.coreprotecttnt.listeners.ExplosionListener;
import com.leir4iks.coreprotecttnt.listeners.FireListener;
import com.leir4iks.coreprotecttnt.listeners.HangingListener;
import com.leir4iks.coreprotecttnt.listeners.TrackingListener;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Main extends JavaPlugin {
   private final Cache<Object, String> probablyCache;
   private final Cache<UUID, Boolean> maceCache;
   private CoreProtectAPI api;

   public Main() {
      this.probablyCache = CacheBuilder.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).concurrencyLevel(4).maximumSize(50000L).build();
      this.maceCache = CacheBuilder.newBuilder().expireAfterWrite(200, TimeUnit.MILLISECONDS).build();
   }

   @Override
   public void onEnable() {
      saveDefaultConfig();
      Plugin depend = Bukkit.getPluginManager().getPlugin("CoreProtect");
      if (depend instanceof CoreProtect) {
         CoreProtectAPI coreProtectAPI = ((CoreProtect) depend).getAPI();
         if (coreProtectAPI.APIVersion() >= 10) {
            this.api = coreProtectAPI;
            registerListeners();
            getLogger().info("Successfully hooked into CoreProtect API v" + coreProtectAPI.APIVersion());
         } else {
            getLogger().severe("CoreProtect API version 10 or higher is required. Disabling plugin.");
            this.getPluginLoader().disablePlugin(this);
         }
      } else {
         getLogger().severe("CoreProtect not found. Disabling plugin.");
         this.getPluginLoader().disablePlugin(this);
      }
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

   public Cache<UUID, Boolean> getMaceCache() {
      return maceCache;
   }
}