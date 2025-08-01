package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;

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
            logger.info("--- Debug: TrackingListener@onBlockPlace ---");
            logger.info("Player: " + event.getPlayer().getName() + " | Block: " + event.getBlock().getType());
        }
        this.plugin.getCache().put(event.getBlock().getLocation(), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("--- Debug: TrackingListener@onBlockBreak ---");
            logger.info("Player: " + event.getPlayer().getName() + " | Block: " + event.getBlock().getType());
        }
        this.plugin.getCache().put(event.getBlock().getLocation(), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("--- Debug: TrackingListener@onProjectileLaunch ---");
            logger.info("Projectile: " + e.getEntity().getType() + " | Shooter: " + (e.getEntity().getShooter() != null ? e.getEntity().getShooter() : "null"));
        }
        Projectile projectile = e.getEntity();
        if (projectile.getShooter() == null) return;
        String shooterName = "world";
        if (projectile.getShooter() instanceof Player) {
            shooterName = ((Player) projectile.getShooter()).getName();
        } else if (projectile.getShooter() instanceof Entity) {
            shooterName = ((Entity) projectile.getShooter()).getName();
        }
        this.plugin.getCache().put(projectile, shooterName);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIgniteTNT(EntitySpawnEvent e) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            if (e.getEntity() instanceof TNTPrimed) {
                logger.info("--- Debug: TrackingListener@onIgniteTNT ---");
                logger.info("Entity: " + e.getEntity().getType() + " | Location: " + e.getLocation());
            }
        }
        if (!(e.getEntity() instanceof TNTPrimed)) return;
        TNTPrimed tntPrimed = (TNTPrimed) e.getEntity();
        Entity source = tntPrimed.getSource();
        if (source != null) {
            String sourceFromCache = this.plugin.getCache().getIfPresent(source);
            if (sourceFromCache != null) {
                this.plugin.getCache().put(tntPrimed, sourceFromCache);
                return;
            }
            if (source instanceof Player) {
                this.plugin.getCache().put(tntPrimed, source.getName());
                return;
            }
        }
        Location blockLocation = tntPrimed.getLocation().getBlock().getLocation();
        String reason = this.plugin.getCache().getIfPresent(blockLocation);
        if (reason != null) {
            this.plugin.getCache().put(tntPrimed, reason);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEndCrystalHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof EnderCrystal) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                logger.info("--- Debug: TrackingListener@onEndCrystalHit ---");
                logger.info("Entity: " + e.getEntity().getType() + " | Damager: " + e.getDamager().getType());
            }
        }

        if (!(e.getEntity() instanceof EnderCrystal)) return;
        String damagerName = null;
        if (e.getDamager() instanceof Player) {
            damagerName = e.getDamager().getName();
        } else {
            damagerName = this.plugin.getCache().getIfPresent(e.getDamager());
            if (damagerName == null && e.getDamager() instanceof Projectile) {
                ProjectileSource shooter = ((Projectile) e.getDamager()).getShooter();
                if (shooter instanceof Player) {
                    damagerName = ((Player) shooter).getName();
                }
            }
        }
        if (damagerName != null) {
            this.plugin.getCache().put(e.getEntity(), damagerName);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityHitByProjectile(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Projectile) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                logger.info("--- Debug: TrackingListener@onEntityHitByProjectile ---");
                logger.info("Entity: " + e.getEntity().getType() + " | Projectile: " + e.getDamager().getType());
            }
        }
        if (!(e.getDamager() instanceof Projectile)) return;
        Projectile projectile = (Projectile) e.getDamager();
        ProjectileSource shooter = projectile.getShooter();
        if (shooter == null) return;
        String sourceName = this.plugin.getCache().getIfPresent(projectile);
        if (sourceName == null) {
            if (shooter instanceof Player) {
                sourceName = ((Player) shooter).getName();
            } else if (shooter instanceof Entity) {
                sourceName = ((Entity) shooter).getName();
            }
        }
        if (sourceName != null) {
            this.plugin.getCache().put(e.getEntity(), sourceName);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBombHit(ProjectileHitEvent e) {
        if (e.getHitEntity() != null) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                logger.info("--- Debug: TrackingListener@onBombHit ---");
                logger.info("Hit Entity: " + e.getHitEntity().getType() + " | Projectile: " + e.getEntity().getType());
            }
        }
        if (e.getHitEntity() == null) return;
        if (!(e.getHitEntity() instanceof ExplosiveMinecart) && e.getHitEntity().getType() != EntityType.END_CRYSTAL) return;
        String source = this.plugin.getCache().getIfPresent(e.getEntity());
        if (source == null && e.getEntity().getShooter() instanceof Player) {
            source = ((Player) e.getEntity().getShooter()).getName();
        }
        if (source != null) {
            this.plugin.getCache().put(e.getHitEntity(), source);
        }
    }
}