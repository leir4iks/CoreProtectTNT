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
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Locale;
import java.util.logging.Logger;

public class TrackingListener implements Listener {
    private final Main plugin;
    private final Logger logger;
    private static final int WITHER_SPAWN_RADIUS = 16;
    private static final int TNT_NEARBY_SOURCE_RADIUS = 5;
    private static final Vector[] WITHER_BODY_VECTORS = {
            new Vector(0, -1, 0),
            new Vector(0, -2, 0),
            new Vector(1, -2, 0),
            new Vector(-1, -2, 0)
    };
    private static final Vector[] WITHER_SKULL_VECTORS = {
            new Vector(0, 0, 0),
            new Vector(1, 0, 0),
            new Vector(-1, 0, 0)
    };

    public TrackingListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Caching block place at " + event.getBlock().getLocation() + " by " + event.getPlayer().getName());
        }
        this.plugin.getBlockPlaceCache().put(event.getBlock().getLocation(), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Caching block break at " + event.getBlock().getLocation() + " by " + event.getPlayer().getName());
        }
        this.plugin.getBlockPlaceCache().put(event.getBlock().getLocation(), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerDamageMob(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player && e.getEntity() instanceof Mob) {
            this.plugin.getEntityAggroCache().put(e.getEntity().getUniqueId(), e.getDamager().getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() == EntityType.WITHER && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
            Location spawnLoc = e.getLocation();
            String placer = null;

            for (Vector vec : WITHER_SKULL_VECTORS) {
                placer = this.plugin.getBlockPlaceCache().getIfPresent(spawnLoc.clone().add(vec));
                if (placer != null) break;
            }

            if (placer == null) {
                for (Vector vec : WITHER_BODY_VECTORS) {
                    placer = this.plugin.getBlockPlaceCache().getIfPresent(spawnLoc.clone().add(vec));
                    if (placer != null) break;
                }
            }

            if (placer != null) {
                this.plugin.getEntityAggroCache().put(e.getEntity().getUniqueId(), placer);
            } else {
                e.getLocation().getNearbyPlayers(WITHER_SPAWN_RADIUS).stream()
                        .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(e.getLocation())))
                        .ifPresent(closestPlayer -> this.plugin.getEntityAggroCache().put(e.getEntity().getUniqueId(), closestPlayer.getName()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        Projectile projectile = e.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (shooter == null) return;

        String finalCause = "world";
        String projectileName = projectile.getType().name().toLowerCase(Locale.ROOT);

        if (shooter instanceof Player player) {
            finalCause = "#" + projectileName + "-" + player.getName();
        } else if (shooter instanceof Mob mob) {
            String mobTypeName = mob.getType().name().toLowerCase(Locale.ROOT);
            String trackedAggressor = this.plugin.getEntityAggroCache().getIfPresent(mob.getUniqueId());
            if (trackedAggressor != null) {
                finalCause = "#" + projectileName + "-" + mobTypeName + "-" + trackedAggressor;
            } else {
                finalCause = "#" + projectileName + "-" + mobTypeName;
            }
        } else if (shooter instanceof BlockProjectileSource bps) {
            Location loc = bps.getBlock().getLocation();
            String blockInitiator = this.plugin.getBlockPlaceCache().getIfPresent(loc);
            if (blockInitiator != null) {
                finalCause = "#" + projectileName + "-" + blockInitiator;
            } else {
                finalCause = "#dispenser@[" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "]";
            }
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Caching projectile " + projectile.getUniqueId() + " with cause: " + finalCause);
        }
        this.plugin.getProjectileCache().put(projectile.getUniqueId(), finalCause);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIgniteTNT(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed tntPrimed)) return;
        Entity source = tntPrimed.getSource();
        String initiator = null;

        if (source != null) {
            initiator = this.plugin.getProjectileCache().getIfPresent(source.getUniqueId());
            if (initiator == null) {
                if (source instanceof Player) {
                    initiator = source.getName();
                }
            }
        }

        if (initiator == null) {
            Location blockLocation = tntPrimed.getLocation().getBlock().getLocation();
            initiator = this.plugin.getBlockPlaceCache().getIfPresent(blockLocation);
        }

        if (initiator == null) {
            Location tntLocation = tntPrimed.getLocation();
            for (int x = -TNT_NEARBY_SOURCE_RADIUS; x <= TNT_NEARBY_SOURCE_RADIUS; x++) {
                for (int y = -TNT_NEARBY_SOURCE_RADIUS; y <= TNT_NEARBY_SOURCE_RADIUS; y++) {
                    for (int z = -TNT_NEARBY_SOURCE_RADIUS; z <= TNT_NEARBY_SOURCE_RADIUS; z++) {
                        Location checkLoc = tntLocation.clone().add(x, y, z);
                        String nearbySource = this.plugin.getBlockPlaceCache().getIfPresent(checkLoc.getBlock().getLocation());
                        if (nearbySource != null) {
                            initiator = nearbySource;
                            if (plugin.getConfig().getBoolean("debug", false)) logger.info("[Debug] Found nearby source for TNT: " + initiator + " at " + checkLoc);
                            break;
                        }
                    }
                    if (initiator != null) break;
                }
                if (initiator != null) break;
            }
        }

        if (initiator != null) {
            if (plugin.getConfig().getBoolean("debug", false)) logger.info("[Debug] Tracking TNT " + tntPrimed.getUniqueId() + " from source: " + initiator);
            this.plugin.getProjectileCache().put(tntPrimed.getUniqueId(), initiator);
        }
    }
}