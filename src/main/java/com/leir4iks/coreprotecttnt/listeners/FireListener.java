package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import com.leir4iks.coreprotecttnt.config.Config;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.util.BoundingBox;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FireListener implements Listener {
    private final Main plugin;
    private final Logger logger;
    private final Map<UUID, Map<Long, Set<FireSource>>> worldChunkFires = new ConcurrentHashMap<>();
    private static final double FIRE_SEARCH_RADIUS = 4.0;
    private static final long FIRE_SOURCE_TIMEOUT = 30000;

    public FireListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        startCleanupTask();
    }

    private static class FireSource {
        String cause;
        BoundingBox boundingBox;
        long lastActivity;
        Location center;

        FireSource(String cause, Location origin) {
            this.cause = cause;
            this.center = origin.clone();
            this.boundingBox = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY() + 1, origin.getZ() + 1);
            this.lastActivity = System.currentTimeMillis();
        }

        void expandTo(Location loc) {
            this.boundingBox.expand(loc.getX(), loc.getY(), loc.getZ());
            this.lastActivity = System.currentTimeMillis();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent e) {
        Config config = plugin.getConfigManager().get();
        if (!config.modules.fire.enabled) return;

        String cause = determineInitialCause(e.getPlayer(), e.getIgnitingBlock(), e.getIgnitingEntity());

        if (cause == null) {
            if (e.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
                cause = Util.createChainedCause(plugin, "lava", null);
            } else if (e.getIgnitingBlock() != null) {
                FireSource existingSource = findSourceFor(e.getIgnitingBlock().getLocation());
                if (existingSource != null) {
                    String prefix = config.formatting.logPrefix;
                    String fireTrans = Util.getTranslatedName(plugin, "fire");
                    String toRemove = prefix + fireTrans + config.formatting.causeSeparator;
                    cause = existingSource.cause.replace(toRemove, "");
                    existingSource.expandTo(e.getBlock().getLocation());
                }
            }
        }

        if (cause != null) {
            String reason = Util.createChainedCause(plugin, "fire", cause);
            addFireSource(e.getBlock().getLocation(), reason);

            if (config.general.debug) {
                logger.info("[Debug] Fire spread tracked: " + reason + " at " + e.getBlock().getLocation());
            }
        } else if (config.modules.fire.disableUnknown) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent e) {
        Config config = plugin.getConfigManager().get();
        if (!config.modules.fire.enabled) return;

        FireSource source = findSourceFor(e.getBlock().getLocation());
        if (source != null) {
            source.expandTo(e.getBlock().getLocation());
            BlockState burnedBlockState = e.getBlock().getState();
            this.plugin.getApi().logRemoval(source.cause, burnedBlockState.getLocation(), burnedBlockState.getType(), burnedBlockState.getBlockData());
        } else if (config.modules.fire.disableUnknown) {
            e.setCancelled(true);
            Util.broadcastNearPlayers(e.getBlock().getLocation(), config.localization.messages.unknownSourceAlert);
        }
    }

    private void addFireSource(Location location, String cause) {
        UUID worldId = location.getWorld().getUID();
        long chunkKey = Main.getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);

        worldChunkFires.computeIfAbsent(worldId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet())
                .add(new FireSource(cause, location));
    }

    private String determineInitialCause(Player player, Block ignitingBlock, org.bukkit.entity.Entity ignitingEntity) {
        if (player != null) {
            return player.getName();
        }
        if (ignitingEntity != null) {
            String fromProjectileCache = this.plugin.getProjectileCache().getIfPresent(ignitingEntity.getUniqueId());
            if (fromProjectileCache != null) {
                return fromProjectileCache;
            }

            if (ignitingEntity instanceof Mob) {
                String fromAggroCache = this.plugin.getEntityAggroCache().getIfPresent(ignitingEntity.getUniqueId());
                if (fromAggroCache != null) {
                    return Util.createChainedCause(plugin, ignitingEntity, fromAggroCache);
                }
            }
            return ignitingEntity.getType().name().toLowerCase(Locale.ROOT);
        }
        if (ignitingBlock != null) {
            return this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(ignitingBlock.getLocation()));
        }
        return null;
    }

    private FireSource findSourceFor(Location location) {
        UUID worldId = location.getWorld().getUID();
        Map<Long, Set<FireSource>> chunkMap = worldChunkFires.get(worldId);
        if (chunkMap == null) return null;

        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                long key = Main.getChunkKey(cx + x, cz + z);
                Set<FireSource> sources = chunkMap.get(key);
                if (sources != null) {
                    for (FireSource source : sources) {
                        if (source.boundingBox.clone().expand(FIRE_SEARCH_RADIUS).contains(location.toVector())) {
                            return source;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void startCleanupTask() {
        Main.getScheduler().runTaskTimerAsynchronously(() -> {
            long now = System.currentTimeMillis();
            worldChunkFires.values().forEach(chunkMap -> {
                chunkMap.values().removeIf(sources -> {
                    sources.removeIf(source -> (now - source.lastActivity) > FIRE_SOURCE_TIMEOUT);
                    return sources.isEmpty();
                });
            });
        }, 200L, 200L);
    }
}