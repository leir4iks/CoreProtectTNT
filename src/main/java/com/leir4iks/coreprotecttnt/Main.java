package com.leir4iks.coreprotecttnt;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.leir4iks.coreprotecttnt.config.ConfigManager;
import com.leir4iks.coreprotecttnt.listeners.*;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Main extends JavaPlugin {
   public record BlockKey(UUID worldId, int x, int y, int z) implements Serializable {
      private static final long serialVersionUID = 1L;
      private static final String DELIMITER = ";";

      public static BlockKey from(Location loc) {
         if (loc == null || loc.getWorld() == null) {
            throw new IllegalArgumentException("Location or world cannot be null");
         }
         return new BlockKey(loc.getWorld().getUID(),
                 loc.getBlockX(),
                 loc.getBlockY(),
                 loc.getBlockZ());
      }

      public static BlockKey from(UUID worldId, int x, int y, int z) {
         if (worldId == null) {
            throw new IllegalArgumentException("WorldId cannot be null");
         }
         return new BlockKey(worldId, x, y, z);
      }

      @Override
      public String toString() {
         return new StringBuilder(64)
                 .append(worldId)
                 .append(DELIMITER)
                 .append(x)
                 .append(DELIMITER)
                 .append(y)
                 .append(DELIMITER)
                 .append(z)
                 .toString();
      }

      public static BlockKey fromString(String str) {
         if (str == null || str.isEmpty()) return null;

         try {
            int firstSep = str.indexOf(DELIMITER);
            int secondSep = str.indexOf(DELIMITER, firstSep + 1);
            int thirdSep = str.indexOf(DELIMITER, secondSep + 1);

            if (firstSep == -1 || secondSep == -1 || thirdSep == -1) return null;

            return new BlockKey(
                    UUID.fromString(str.substring(0, firstSep)),
                    Integer.parseInt(str.substring(firstSep + 1, secondSep)),
                    Integer.parseInt(str.substring(secondSep + 1, thirdSep)),
                    Integer.parseInt(str.substring(thirdSep + 1))
            );
         } catch (Exception e) {
            return null;
         }
      }

      public RegionKey getRegionKey() {
         int regX = x >> 9;
         int regZ = z >> 9;
         return new RegionKey(worldId, regX, regZ);
      }
   }

   public record RegionKey(UUID worldId, int regionX, int regionZ) {
      @Override
      public String toString() {
         return worldId + ":" + regionX + ":" + regionZ;
      }
   }

   private static class ChainReaction {
      final String cause;
      BoundingBox boundingBox;
      final UUID worldId;
      volatile long lastActivity;

      ChainReaction(String cause, Location center, double radius) {
         this.cause = cause;
         this.worldId = center.getWorld().getUID();
         this.boundingBox = BoundingBox.of(
                 center.clone().subtract(radius, radius, radius),
                 center.clone().add(radius, radius, radius)
         );
         this.lastActivity = System.currentTimeMillis();
      }

      boolean overlaps(Location location, double radius) {
         if (location.getWorld() == null || !location.getWorld().getUID().equals(worldId))
            return false;

         double halfRadius = radius / 2.0;
         return boundingBox.clone().expand(radius, radius, radius)
                 .overlaps(BoundingBox.of(
                         location.clone().subtract(halfRadius, halfRadius, halfRadius),
                         location.clone().add(halfRadius, halfRadius, halfRadius)
                 ));
      }

      boolean contains(Location location) {
         if (location.getWorld() == null || !location.getWorld().getUID().equals(worldId))
            return false;
         return boundingBox.contains(location.toVector());
      }

      void expand(Location location, double radius) {
         double halfRadius = radius / 2.0;
         this.boundingBox = this.boundingBox.union(BoundingBox.of(
                 location.clone().subtract(halfRadius, halfRadius, halfRadius),
                 location.clone().add(halfRadius, halfRadius, halfRadius)
         ));
         this.lastActivity = System.currentTimeMillis();
      }
   }

   private static final int CACHE_CONCURRENCY_LEVEL = Math.max(2, Runtime.getRuntime().availableProcessors());

   private final Cache<BlockKey, String> blockPlaceCache = CacheBuilder.newBuilder()
           .expireAfterWrite(3, TimeUnit.HOURS)
           .concurrencyLevel(CACHE_CONCURRENCY_LEVEL)
           .initialCapacity(1000)
           .maximumSize(50000)
           .recordStats()
           .build();

   private final Cache<UUID, String> entityAggroCache = CacheBuilder.newBuilder()
           .expireAfterWrite(30, TimeUnit.MINUTES)
           .concurrencyLevel(CACHE_CONCURRENCY_LEVEL)
           .build();

   private final Cache<UUID, String> projectileCache = CacheBuilder.newBuilder()
           .expireAfterWrite(10, TimeUnit.MINUTES)
           .concurrencyLevel(CACHE_CONCURRENCY_LEVEL)
           .build();

   private final Cache<UUID, Boolean> processedEntities = CacheBuilder.newBuilder()
           .expireAfterWrite(2, TimeUnit.SECONDS)
           .concurrencyLevel(CACHE_CONCURRENCY_LEVEL)
           .build();

   private final Cache<BlockKey, String> dispenserCache = CacheBuilder.newBuilder()
           .expireAfterWrite(1, TimeUnit.SECONDS)
           .concurrencyLevel(CACHE_CONCURRENCY_LEVEL)
           .build();

   private final Cache<UUID, String> playerProjectileCache = CacheBuilder.newBuilder()
           .expireAfterWrite(5, TimeUnit.SECONDS)
           .concurrencyLevel(CACHE_CONCURRENCY_LEVEL)
           .build();

   private final Map<UUID, Map<Long, Set<ChainReaction>>> worldChunkChains = new ConcurrentHashMap<>();
   private final Map<UUID, String> dragonRespawners = new ConcurrentHashMap<>();

   private final ExecutorService ioExecutor = Executors.newFixedThreadPool(
           Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
           r -> {
              Thread t = new Thread(r, "CoreProtectTNT-IO");
              t.setDaemon(true);
              t.setPriority(Thread.MIN_PRIORITY + 1);
              return t;
           }
   );

   private final ExecutorService computeExecutor = Executors.newWorkStealingPool(
           Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
   );

   private CoreProtectAPI api;
   private UpdateChecker updateChecker;
   private ConfigManager configManager;
   private static TaskScheduler scheduler;
   private Path cacheDir;
   private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

   private volatile boolean isCacheLoaded = false;
   private final CountDownLatch cacheLoadLatch = new CountDownLatch(1);

   @Override
   public void onEnable() {
      long startTime = System.currentTimeMillis();

      try {
         this.configManager = new ConfigManager(this);
         scheduler = UniversalScheduler.getScheduler(this);
         this.cacheDir = getDataFolder().toPath().resolve("cache");

         Files.createDirectories(cacheDir);

         if (!setupCoreProtect()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
         }

         loadCacheAsync();

         try {
            if (!cacheLoadLatch.await(5, TimeUnit.SECONDS)) {
               getLogger().warning("Cache loading is taking longer than expected...");
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }

         registerListeners();
         this.updateChecker = new UpdateChecker(this);

         computeExecutor.submit(() -> {
            try {
               updateChecker.check();
            } catch (Exception e) {
               getLogger().log(Level.WARNING, "Failed to check for updates", e);
            }
         });

         if (getCommand("cptnt") != null) {
            Objects.requireNonNull(getCommand("cptnt")).setExecutor(new UpdateCommand(this));
         }

         startChainCleanupTask();
         startAutoSaveTask();
         startCacheStatsTask();

         new Metrics(this, 26755);

         long enableTime = System.currentTimeMillis() - startTime;
         getLogger().info(String.format(
                 "CoreProtectTNT has been successfully enabled in %d ms. Cache loaded: %s",
                 enableTime, isCacheLoaded
         ));

      } catch (Exception e) {
         getLogger().log(Level.SEVERE, "Failed to enable CoreProtectTNT", e);
         getServer().getPluginManager().disablePlugin(this);
      }
   }

   @Override
   public void onDisable() {
      long startTime = System.currentTimeMillis();

      try {
         isCacheLoaded = false;

         shutdownExecutor(ioExecutor, "IO");
         shutdownExecutor(computeExecutor, "Compute");

         saveCacheSync();

         long disableTime = System.currentTimeMillis() - startTime;
         getLogger().info(String.format(
                 "CoreProtectTNT has been disabled in %d ms.",
                 disableTime
         ));

      } catch (Exception e) {
         getLogger().log(Level.SEVERE, "Error during disable", e);
      }
   }

   private void shutdownExecutor(ExecutorService executor, String name) {
      if (executor == null) return;

      executor.shutdown();
      try {
         if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
         }
      } catch (InterruptedException e) {
         executor.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }

   private void saveCacheSync() {
      long start = System.currentTimeMillis();
      try {
         saveCacheData();
         getLogger().info(String.format("Cache saved in %dms", System.currentTimeMillis() - start));
      } catch (Exception e) {
         getLogger().log(Level.SEVERE, "Failed to save cache", e);
      }
   }

   private void saveCacheAsync() {
      if (!isCacheLoaded || ioExecutor.isShutdown()) {
         return;
      }

      ioExecutor.submit(() -> {
         try {
            saveCacheData();
         } catch (Exception e) {
            getLogger().log(Level.WARNING, "Async cache save failed", e);
         }
      });
   }

   private void saveCacheData() throws IOException {
      if (!Files.exists(cacheDir)) {
         Files.createDirectories(cacheDir);
      }

      Map<RegionKey, List<Map.Entry<BlockKey, String>>> groupedData = blockPlaceCache.asMap().entrySet().stream()
              .collect(Collectors.groupingBy(e -> e.getKey().getRegionKey()));

      AtomicInteger savedFiles = new AtomicInteger();
      AtomicLong savedEntries = new AtomicLong();

      groupedData.forEach((regionKey, entries) -> {
         if (entries.isEmpty()) return;

         try {
            Path worldDir = cacheDir.resolve(regionKey.worldId().toString());
            Files.createDirectories(worldDir);

            Path regionFile = worldDir.resolve(String.format("r.%d.%d.json.gz",
                    regionKey.regionX(), regionKey.regionZ()));

            Path tempFile = regionFile.resolveSibling(regionFile.getFileName() + ".tmp");

            try (OutputStream fos = Files.newOutputStream(tempFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 GZIPOutputStream gzip = new GZIPOutputStream(fos, 8192);
                 Writer writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8);
                 JsonWriter jsonWriter = GSON.newJsonWriter(writer)) {

               JsonObject json = new JsonObject();
               for (Map.Entry<BlockKey, String> entry : entries) {
                  json.addProperty(entry.getKey().toString(), entry.getValue());
               }
               GSON.toJson(json, JsonObject.class, jsonWriter);
            }

            Files.move(tempFile, regionFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            savedFiles.incrementAndGet();
            savedEntries.addAndGet(entries.size());

         } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to save region: " + regionKey, e);
         }
      });

      if (!dragonRespawners.isEmpty()) {
         Path dragonFile = cacheDir.resolve("dragons.json.gz");
         Path tempDragonFile = cacheDir.resolve("dragons.json.gz.tmp");

         try (OutputStream fos = Files.newOutputStream(tempDragonFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
              GZIPOutputStream gzip = new GZIPOutputStream(fos, 8192);
              Writer writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8);
              JsonWriter jsonWriter = GSON.newJsonWriter(writer)) {

            JsonObject json = new JsonObject();
            dragonRespawners.forEach((k, v) -> json.addProperty(k.toString(), v));
            GSON.toJson(json, JsonObject.class, jsonWriter);
         }
         Files.move(tempDragonFile, dragonFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      }

      getLogger().fine(String.format("Saved %d region files with %d entries", savedFiles.get(), savedEntries.get()));
   }

   private void loadCacheAsync() {
      ioExecutor.submit(() -> {
         long start = System.currentTimeMillis();
         try {
            loadCacheData();
            isCacheLoaded = true;
            getLogger().info(String.format("Cache loaded in %dms", System.currentTimeMillis() - start));
         } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load cache", e);
         } finally {
            cacheLoadLatch.countDown();
         }
      });
   }

   private void loadCacheData() throws IOException {
      if (!Files.exists(cacheDir)) {
         return;
      }

      long expireTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3);
      AtomicInteger loadedEntries = new AtomicInteger();
      AtomicInteger skippedEntries = new AtomicInteger();

      Set<UUID> existingWorlds = new HashSet<>();
      for (World world : Bukkit.getWorlds()) {
         existingWorlds.add(world.getUID());
      }

      try (var worldDirs = Files.list(cacheDir)) {
         worldDirs.filter(Files::isDirectory).forEach(worldDir -> {
            try {
               UUID worldId;
               try {
                  worldId = UUID.fromString(worldDir.getFileName().toString());
               } catch (IllegalArgumentException e) {
                  return;
               }

               if (!existingWorlds.contains(worldId)) {
                  return;
               }

               try (var regionFiles = Files.list(worldDir)
                       .filter(f -> f.getFileName().toString().endsWith(".json.gz"))
                       .parallel()) {

                  regionFiles.forEach(regionFile -> {
                     try {
                        if (Files.getLastModifiedTime(regionFile).toMillis() < expireTime) {
                           try {
                              Files.deleteIfExists(regionFile);
                           } catch (IOException ignored) {}
                           return;
                        }

                        loadRegionFile(regionFile, loadedEntries, skippedEntries);
                     } catch (IOException e) {
                        getLogger().log(Level.WARNING,
                                "Failed to process region file: " + regionFile.getFileName(), e);
                     }
                  });
               }
            } catch (IOException e) {
               getLogger().log(Level.WARNING, "Error reading world directory", e);
            }
         });
      }

      Path dragonFile = cacheDir.resolve("dragons.json.gz");
      if (Files.exists(dragonFile)) {
         try (InputStream fis = Files.newInputStream(dragonFile);
              GZIPInputStream gzip = new GZIPInputStream(fis, 8192);
              Reader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
              JsonReader jsonReader = GSON.newJsonReader(reader)) {

            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
               String key = jsonReader.nextName();
               String value = jsonReader.nextString();
               try {
                  dragonRespawners.putIfAbsent(UUID.fromString(key), value);
               } catch (IllegalArgumentException ignored) {}
            }
            jsonReader.endObject();
         } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to load dragon cache", e);
         }
      }

      getLogger().info(String.format("Loaded %d cache entries, skipped %d outdated",
              loadedEntries.get(), skippedEntries.get()));
   }

   private void loadRegionFile(Path regionFile, AtomicInteger loadedCounter, AtomicInteger skippedCounter) throws IOException {
      try (InputStream fis = Files.newInputStream(regionFile);
           GZIPInputStream gzip = new GZIPInputStream(fis, 8192);
           Reader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
           JsonReader jsonReader = GSON.newJsonReader(reader)) {

         jsonReader.beginObject();
         while (jsonReader.hasNext()) {
            String keyStr = jsonReader.nextName();
            String value = jsonReader.nextString();

            BlockKey key = BlockKey.fromString(keyStr);
            if (key != null) {
               if (blockPlaceCache.getIfPresent(key) == null) {
                  blockPlaceCache.put(key, value);
                  loadedCounter.incrementAndGet();
               } else {
                  skippedCounter.incrementAndGet();
               }
            }
         }
         jsonReader.endObject();
      }
   }

   private boolean setupCoreProtect() {
      Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
      if (!(plugin instanceof CoreProtect)) {
         getLogger().severe("CoreProtect not found! CoreProtectTNT will not function without it.");
         getLogger().severe("Please download it from: https://github.com/PlayPro/CoreProtect/releases");
         return false;
      }

      CoreProtect coreProtect = (CoreProtect) plugin;
      CoreProtectAPI coreProtectAPI = coreProtect.getAPI();

      if (coreProtectAPI == null) {
         getLogger().severe("Failed to get CoreProtect API!");
         return false;
      }

      if (coreProtectAPI.APIVersion() < 9) {
         getLogger().severe("Your version of CoreProtect is too old!");
         getLogger().severe("CoreProtectTNT requires API version 9 or higher.");
         return false;
      }

      if (!coreProtect.isEnabled()) {
         getLogger().severe("CoreProtect is not enabled!");
         return false;
      }

      this.api = coreProtectAPI;
      getLogger().info("Successfully hooked into CoreProtect API v" + coreProtectAPI.APIVersion());
      return true;
   }

   private void registerListeners() {
      Bukkit.getPluginManager().registerEvents(new ExplosionListener(this), this);
      Bukkit.getPluginManager().registerEvents(new FireListener(this), this);
      Bukkit.getPluginManager().registerEvents(new HangingListener(this), this);
      Bukkit.getPluginManager().registerEvents(new TrackingListener(this), this);
      Bukkit.getPluginManager().registerEvents(new FrameListener(this), this);
   }

   private void startChainCleanupTask() {
      scheduler.runTaskTimerAsynchronously(() -> {
         long now = System.currentTimeMillis();
         worldChunkChains.values().forEach(chunkMap -> {
            chunkMap.values().removeIf(chains -> {
               chains.removeIf(chain -> (now - chain.lastActivity) > 2000);
               return chains.isEmpty();
            });
         });
      }, 20L, 20L);
   }

   private void startAutoSaveTask() {
      scheduler.runTaskTimerAsynchronously(() -> {
         if (isCacheLoaded) {
            saveCacheAsync();
         }
      }, 6000L, 6000L);
   }

   private void startCacheStatsTask() {
      scheduler.runTaskTimerAsynchronously(() -> {
         if (isCacheLoaded) {
            getLogger().info(String.format(
                    "Cache stats - BlockPlace: size=%d, hits=%d, misses=%d, hitRate=%.2f%%",
                    blockPlaceCache.size(),
                    blockPlaceCache.stats().hitCount(),
                    blockPlaceCache.stats().missCount(),
                    blockPlaceCache.stats().hitRate() * 100
            ));
         }
      }, 12000L, 12000L);
   }

   public static long getChunkKey(int x, int z) {
      return (long) x & 0xffffffffL | ((long) z & 0xffffffffL) << 32;
   }

   public void addExplosion(Location location, double radius, String cause) {
      if (location == null || location.getWorld() == null || cause == null) {
         return;
      }

      UUID worldId = location.getWorld().getUID();
      long chunkKey = getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);

      Map<Long, Set<ChainReaction>> chunkMap = worldChunkChains.computeIfAbsent(worldId, k -> new ConcurrentHashMap<>());
      Set<ChainReaction> chains = chunkMap.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet());

      boolean merged = false;
      for (ChainReaction chain : chains) {
         if (chain.overlaps(location, radius)) {
            chain.expand(location, radius);
            merged = true;
            break;
         }
      }

      if (!merged) {
         chains.add(new ChainReaction(cause, location, radius));
      }
   }

   public String getExplosionSource(Location location) {
      if (location == null || location.getWorld() == null) {
         return null;
      }

      UUID worldId = location.getWorld().getUID();
      Map<Long, Set<ChainReaction>> chunkMap = worldChunkChains.get(worldId);
      if (chunkMap == null) return null;

      int cx = location.getBlockX() >> 4;
      int cz = location.getBlockZ() >> 4;

      for (int x = -1; x <= 1; x++) {
         for (int z = -1; z <= 1; z++) {
            long key = getChunkKey(cx + x, cz + z);
            Set<ChainReaction> chains = chunkMap.get(key);
            if (chains != null) {
               for (ChainReaction chain : chains) {
                  if (chain.contains(location)) {
                     return chain.cause;
                  }
               }
            }
         }
      }
      return null;
   }

   public CoreProtectAPI getApi() { return api; }
   public static TaskScheduler getScheduler() { return scheduler; }
   public ConfigManager getConfigManager() { return configManager; }
   public UpdateChecker getUpdateChecker() { return updateChecker; }
   public Cache<BlockKey, String> getBlockPlaceCache() { return blockPlaceCache; }
   public Cache<UUID, String> getEntityAggroCache() { return entityAggroCache; }
   public Cache<UUID, String> getProjectileCache() { return projectileCache; }
   public Cache<UUID, Boolean> getProcessedEntities() { return processedEntities; }
   public Cache<BlockKey, String> getDispenserCache() { return dispenserCache; }
   public Cache<UUID, String> getPlayerProjectileCache() { return playerProjectileCache; }
   public Map<UUID, String> getDragonRespawners() { return dragonRespawners; }
}