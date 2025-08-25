package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public class FireListener implements Listener {
    private final Main plugin;
    private final Logger logger;
    private final ConcurrentMap<UUID, FireSource> activeFires = new ConcurrentHashMap<>();
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
        FireSource(String cause, Location origin) {
            this.cause = cause;
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
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Event: BlockIgniteEvent | Block: " + e.getBlock().getType() + " | Cause: " + e.getCause());
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "fire");
        if (!section.getBoolean("enable", true)) return;

        String cause = determineInitialCause(e.getPlayer(), e.getIgnitingBlock(), e.getIgnitingEntity());
        if (cause == null && e.getIgnitingBlock() != null) {
            FireSource existingSource = findSourceFor(e.getIgnitingBlock().getLocation());
            if (existingSource != null) {
                cause = existingSource.cause.replace("#fire-", "");
                existingSource.expandTo(e.getBlock().getLocation());
            }
        }

        if (cause != null) {
            String reason = "#fire-" + cause;
            activeFires.put(UUID.randomUUID(), new FireSource(reason, e.getBlock().getLocation()));
        } else if (section.getBoolean("disable-unknown", false)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent e) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Event: BlockBurnEvent | Block: " + e.getBlock().getType());
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "fire");
        if (!section.getBoolean("enable", true)) return;

        FireSource source = findSourceFor(e.getBlock().getLocation());
        if (source != null) {
            source.expandTo(e.getBlock().getLocation());
            BlockState burnedBlockState = e.getBlock().getState();
            this.plugin.getApi().logRemoval(source.cause, burnedBlockState.getLocation(), burnedBlockState.getType(), burnedBlockState.getBlockData());
        } else if (section.getBoolean("disable-unknown", false)) {
            e.setCancelled(true);
            Util.broadcastNearPlayers(e.getBlock().getLocation(), section.getString("alert"));
        }
    }

    private String determineInitialCause(Player player, Block ignitingBlock, org.bukkit.entity.Entity ignitingEntity) {
        if (player != null) {
            return player.getName();
        }
        if (ignitingEntity != null) {
            String fromCache = this.plugin.getCache().getIfPresent(ignitingEntity.getUniqueId());
            if (fromCache != null) {
                return fromCache;
            }

            if (ignitingEntity instanceof Mob mob) {
                String aggressor = this.plugin.getCache().getIfPresent(mob.getUniqueId());
                if (aggressor != null) {
                    return mob.getType().name().toLowerCase(Locale.ROOT) + "-" + aggressor;
                }
            }
            return ignitingEntity.getType().name().toLowerCase(Locale.ROOT);
        }
        if (ignitingBlock != null) {
            return this.plugin.getCache().getIfPresent(ignitingBlock.getLocation());
        }
        return null;
    }

    private FireSource findSourceFor(Location location) {
        for (FireSource source : activeFires.values()) {
            if (source.boundingBox.clone().expand(FIRE_SEARCH_RADIUS).contains(location.toVector())) {
                return source;
            }
        }
        return null;
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                activeFires.values().removeIf(source -> (now - source.lastActivity) > FIRE_SOURCE_TIMEOUT);
            }
        }.runTaskTimerAsynchronously(this.plugin, 200L, 200L);
    }
}