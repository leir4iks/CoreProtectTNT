package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Iterator;
import java.util.Locale;

public class ExplosionListener implements Listener {
    private final Main plugin;

    public ExplosionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractBedOrRespawnAnchorExplosion(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null) return;
        BlockData blockData = clickedBlock.getBlockData();
        if (blockData instanceof Bed) {
            Bed bed = (Bed) blockData;
            Location headLocation = (bed.getPart() == Bed.Part.HEAD) ? clickedBlock.getLocation() : clickedBlock.getLocation().add(bed.getFacing().getDirection());
            Location footLocation = (bed.getPart() == Bed.Part.FOOT) ? clickedBlock.getLocation() : clickedBlock.getLocation().subtract(bed.getFacing().getDirection());
            String reason = "#bed-" + e.getPlayer().getName();
            this.plugin.getCache().put(headLocation.getBlock().getLocation(), reason);
            this.plugin.getCache().put(footLocation.getBlock().getLocation(), reason);
        } else if (blockData instanceof RespawnAnchor) {
            this.plugin.getCache().put(clickedBlock.getLocation(), "#respawnanchor-" + e.getPlayer().getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractCreeper(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Creeper && e.getPlayer().getInventory().getItemInMainHand().getType() == Material.FLINT_AND_STEEL) {
            this.plugin.getCache().put(e.getRightClicked(), "#ignitecreeper-" + e.getPlayer().getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "block-explosion");
        if (!section.getBoolean("enable", true)) return;
        Location location = e.getBlock().getLocation();
        String probablyCauses = this.plugin.getCache().getIfPresent(location);
        if (probablyCauses == null) {
            probablyCauses = this.plugin.getCache().getIfPresent(location.getBlock().getLocation());
        }
        if (probablyCauses == null) {
            if (section.getBoolean("disable-unknown", false)) {
                e.blockList().clear();
                Util.broadcastNearPlayers(location, section.getString("alert"));
            }
            return;
        }
        for (Block block : e.blockList()) {
            this.plugin.getApi().logRemoval(probablyCauses, block.getLocation(), block.getType(), block.getBlockData());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent e) {
        EntityType entityType = e.getEntityType();
        String entityName = entityType.name();

        if (entityName.equals("BLOCK_AND_ENTITY_SMASHER")) {
            e.blockList().clear();
            return;
        }

        if (entityName.equals("WIND_CHARGE") || entityName.equals("BREEZE_WIND_CHARGE")) {
            if (e.getEntity() instanceof Projectile) {
                Projectile projectile = (Projectile) e.getEntity();
                ProjectileSource source = projectile.getShooter();
                if (source instanceof Player) {
                    e.blockList().clear();
                    return;
                }
            }

            Iterator<Block> iterator = e.blockList().iterator();
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (!Tag.DOORS.isTagged(block.getType()) && !Tag.TRAPDOORS.isTagged(block.getType())) {
                    iterator.remove();
                }
            }
            if (e.blockList().isEmpty()) {
                return;
            }
        }

        if (e.blockList().isEmpty()) return;

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "entity-explosion");
        if (!section.getBoolean("enable", true)) return;

        Entity entity = e.getEntity();
        String track = this.plugin.getCache().getIfPresent(entity);

        if (track == null) {
            if (entity instanceof Creeper) {
                LivingEntity target = ((Creeper) entity).getTarget();
                if (target != null) {
                    track = target.getName();
                }
            } else if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) entity.getLastDamageCause()).getDamager();
                String damagerTrack = this.plugin.getCache().getIfPresent(damager);
                if (damagerTrack != null) {
                    track = damagerTrack;
                } else {
                    track = "#" + damager.getType().name().toLowerCase(Locale.ROOT);
                }
            }
        }

        if (track == null) {
            if (section.getBoolean("disable-unknown", false)) {
                e.blockList().clear();
                Util.broadcastNearPlayers(e.getLocation(), section.getString("alert"));
            }
            return;
        }

        String reason;
        if (track.startsWith("#")) {
            reason = track;
        } else {
            reason = "#" + entityType.name().toLowerCase(Locale.ROOT) + "-" + track;
        }

        for (Block block : e.blockList()) {
            this.plugin.getApi().logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
            this.plugin.getCache().put(block.getLocation(), reason);
        }
        this.plugin.getCache().invalidate(entity);
    }
}