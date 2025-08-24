package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;
import java.util.logging.Logger;

public class TrackingListener implements Listener {
    private final Main plugin;
    private final Logger logger;

    public TrackingListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Caching block place at " + event.getBlock().getLocation() + " by " + event.getPlayer().getName());
        }
        this.plugin.getCache().put(event.getBlock().getLocation(), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Caching block break at " + event.getBlock().getLocation() + " by " + event.getPlayer().getName());
        }
        this.plugin.getCache().put(event.getBlock().getLocation(), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerDamageMob(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player && e.getEntity() instanceof Mob) {
            this.plugin.getCache().put(e.getEntity().getUniqueId(), e.getDamager().getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() == EntityType.WITHER && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
            Player closestPlayer = null;
            double closestDistance = Double.MAX_VALUE;
            for (Player player : e.getLocation().getNearbyPlayers(16)) {
                double distance = player.getLocation().distanceSquared(e.getLocation());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = player;
                }
            }
            if (closestPlayer != null) {
                this.plugin.getCache().put(e.getEntity().getUniqueId(), closestPlayer.getName());
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        Projectile projectile = e.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (shooter == null) return;

        String finalCause;
        if (shooter instanceof Player player) {
            finalCause = player.getName();
        } else if (shooter instanceof Mob mob && mob.getTarget() instanceof Player targetPlayer) {
            finalCause = mob.getType().name().toLowerCase(Locale.ROOT) + "-" + targetPlayer.getName();
        } else if (shooter instanceof Entity entity) {
            finalCause = entity.getType().name().toLowerCase(Locale.ROOT);
        } else if (shooter instanceof BlockProjectileSource bps) {
            Location loc = bps.getBlock().getLocation();
            finalCause = "#dispenser@[" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "]";
        } else {
            finalCause = "world";
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Caching projectile " + projectile.getUniqueId() + " with cause: " + finalCause);
        }
        this.plugin.getCache().put(projectile.getUniqueId(), finalCause);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIgniteTNT(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed tntPrimed)) return;
        Entity source = tntPrimed.getSource();
        String reason = null;

        if (source != null) {
            String sourceReason = this.plugin.getCache().getIfPresent(source.getUniqueId());
            if (sourceReason != null) {
                reason = Util.createChainedCause(source, sourceReason);
            } else if (source instanceof Player) {
                reason = source.getName();
            }
        }

        if (reason == null) {
            Location blockLocation = tntPrimed.getLocation().getBlock().getLocation();
            reason = this.plugin.getCache().getIfPresent(blockLocation);
        }

        if (reason == null) {
            Location tntLocation = tntPrimed.getLocation();
            for (int x = -5; x <= 5; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -5; z <= 5; z++) {
                        Location checkLoc = tntLocation.clone().add(x, y, z);
                        String nearbySource = this.plugin.getCache().getIfPresent(checkLoc.getBlock().getLocation());
                        if (nearbySource != null) {
                            reason = nearbySource;
                            if (plugin.getConfig().getBoolean("debug", false)) logger.info("[Debug] Found nearby source for TNT: " + reason + " at " + checkLoc);
                            break;
                        }
                    }
                    if (reason != null) break;
                }
                if (reason != null) break;
            }
        }

        if (reason != null) {
            if (plugin.getConfig().getBoolean("debug", false)) logger.info("[Debug] Tracking TNT " + tntPrimed.getUniqueId() + " from source: " + reason);
            this.plugin.getCache().put(tntPrimed.getUniqueId(), reason);
        }
    }
}