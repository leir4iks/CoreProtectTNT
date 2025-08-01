package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ExplosionListener implements Listener {
    private final Main plugin;

    public ExplosionListener(Main plugin) {
        this.plugin = plugin;
    }

    private void toggleOpenable(Block block) {
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Openable openable) {
            openable.setOpen(!openable.isOpen());
            block.setBlockData(openable, true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (e.getBlock().getType() == Material.AIR) {
            Location explosionCenter = e.getBlock().getLocation();
            for (Entity nearbyEntity : explosionCenter.getWorld().getNearbyEntities(explosionCenter, 2.0, 2.0, 2.0)) {
                if (nearbyEntity instanceof Player player) {
                    if (player.getInventory().getItemInMainHand().getType() == Material.MACE ||
                            player.getInventory().getItemInOffHand().getType() == Material.MACE) {

                        List<Block> affectedBlocks = new ArrayList<>(e.blockList());
                        e.blockList().clear();

                        String reason = "#mace-" + player.getName();
                        for (Block block : affectedBlocks) {
                            if (Tag.DOORS.isTagged(block.getType()) || Tag.TRAPDOORS.isTagged(block.getType())) {
                                if (block.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP) {
                                    continue;
                                }
                                toggleOpenable(block);
                                this.plugin.getApi().logInteraction(reason, block.getLocation());
                            }
                        }
                        return;
                    }
                }
            }
        }

        if (e.getYield() == 0.0f) {
            e.blockList().clear();
            return;
        }

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
        if (e.getEntityType() == EntityType.WIND_CHARGE) {
            List<Block> affectedBlocks = new ArrayList<>(e.blockList());
            e.blockList().clear();

            String reason;
            String shooterName = this.plugin.getCache().getIfPresent(e.getEntity().getUniqueId());
            if (shooterName == null) shooterName = "world";

            boolean isMace = false;
            Location explosionCenter = e.getLocation();
            for (Entity nearbyEntity : explosionCenter.getWorld().getNearbyEntities(explosionCenter, 1.5, 1.5, 1.5)) {
                if (nearbyEntity instanceof Player player && player.getName().equals(shooterName)) {
                    if (player.getInventory().getItemInMainHand().getType() == Material.MACE ||
                            player.getInventory().getItemInOffHand().getType() == Material.MACE) {
                        isMace = true;
                        break;
                    }
                }
            }

            reason = (isMace ? "#mace-" : "#wind_charge-") + shooterName;

            for (Block block : affectedBlocks) {
                if (Tag.DOORS.isTagged(block.getType()) || Tag.TRAPDOORS.isTagged(block.getType())) {
                    if (block.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP) {
                        continue;
                    }
                    toggleOpenable(block);
                    this.plugin.getApi().logInteraction(reason, block.getLocation());
                }
            }
            return;
        }

        if (e.getYield() == 0.0f) {
            e.blockList().clear();
            return;
        }

        if (e.blockList().isEmpty()) return;

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "entity-explosion");
        if (!section.getBoolean("enable", true)) return;

        Entity entity = e.getEntity();
        String track = this.plugin.getCache().getIfPresent(entity.getUniqueId());

        if (track == null) track = this.plugin.getCache().getIfPresent(entity.getLocation());

        if (track == null) {
            if (entity instanceof Creeper) {
                LivingEntity target = ((Creeper) entity).getTarget();
                if (target != null) track = target.getName();
            } else if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent event) {
                Entity damager = event.getDamager();
                String damagerTrack = this.plugin.getCache().getIfPresent(damager.getUniqueId());
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

        String reason = track.startsWith("#") ? track : "#" + e.getEntityType().name().toLowerCase(Locale.ROOT) + "-" + track;
        for (Block block : e.blockList()) {
            this.plugin.getApi().logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
            this.plugin.getCache().put(block.getLocation(), reason);
        }
        this.plugin.getCache().invalidate(entity.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractBedOrRespawnAnchorExplosion(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null) return;
        if (clickedBlock.getBlockData() instanceof Bed bed) {
            Location headLocation = (bed.getPart() == Bed.Part.HEAD) ? clickedBlock.getLocation() : clickedBlock.getLocation().add(bed.getFacing().getDirection());
            Location footLocation = (bed.getPart() == Bed.Part.FOOT) ? clickedBlock.getLocation() : clickedBlock.getLocation().subtract(bed.getFacing().getDirection());
            String reason = "#bed-" + e.getPlayer().getName();
            this.plugin.getCache().put(headLocation.getBlock().getLocation(), reason);
            this.plugin.getCache().put(footLocation.getBlock().getLocation(), reason);
        } else if (clickedBlock.getBlockData() instanceof RespawnAnchor) {
            this.plugin.getCache().put(clickedBlock.getLocation(), "#respawnanchor-" + e.getPlayer().getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractCreeper(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Creeper && e.getPlayer().getInventory().getItemInMainHand().getType() == Material.FLINT_AND_STEEL) {
            this.plugin.getCache().put(e.getRightClicked().getUniqueId(), "#ignitecreeper-" + e.getPlayer().getName());
        }
    }
}