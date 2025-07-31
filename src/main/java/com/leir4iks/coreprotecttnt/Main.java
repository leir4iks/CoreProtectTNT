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

import java.util.concurrent.TimeUnit;

public class Main extends JavaPlugin {
   private final Cache<Object, String> probablyCache;
   private CoreProtectAPI api;

   public Main() {
      this.probablyCache = CacheBuilder.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).concurrencyLevel(4).maximumSize(50000L).build();
   }

   @Override
   public void onEnable() {
      saveDefaultConfig();
      Plugin depend = Bukkit.getPluginManager().getPlugin("CoreProtect");
      if (depend instanceof CoreProtect) {
         this.api = ((CoreProtect) depend).getAPI();
         registerListeners();
      } else {
         getLogger().severe("CoreProtect not found or invalid version. Disabling plugin.");
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
}