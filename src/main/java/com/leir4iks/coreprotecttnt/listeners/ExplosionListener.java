package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FireListener implements Listener {

    private final Main plugin;
    private final ConcurrentMap<UUID, FireSource> activeFires = new ConcurrentHashMap<>();

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

    public FireListener(Main plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "fire");
        if (!section.getBoolean("enable", true)) return;

        String cause = determineInitialCause(e.getPlayer(), e.getIgnitingBlock(), e.getIgnitingEntity());
        if (cause == null && e.getIgnitingBlock() != null) {
            FireSource existingSource = findSourceFor(e.getIgnitingBlock().getLocation());
            if (existingSource != null) {
                cause = existingSource.cause;
                existingSource.expandTo(e.getBlock().getLocation());
            }
        }

        if (cause != null) {
            String reason = cause.startsWith("#") ? cause : "#fire-" + cause;
            activeFires.put(UUID.randomUUID(), new FireSource(reason, e.getBlock().getLocation()));
        } else if (section.getBoolean("disable-unknown", false)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent e) {
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
            String fromCache = this.plugin.getCache().getIfPresent(ignitingEntity);
            return fromCache != null ? fromCache : "#" + ignitingEntity.getType().name().toLowerCase(Locale.ROOT);
        }
        if (ignitingBlock != null) {
            return this.plugin.getCache().getIfPresent(ignitingBlock.getLocation());
        }
        return null;
    }

    private FireSource findSourceFor(Location location) {
        for (FireSource source : activeFires.values()) {
            if (source.boundingBox.clone().expand(4.0).contains(location.toVector())) {
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
                long timeout = 30000;
                activeFires.values().removeIf(source -> (now - source.lastActivity) > timeout);
            }
        }.runTaskTimerAsynchronously(this.plugin, 200L, 200L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFireFade(BlockFadeEvent e) {
    }
}