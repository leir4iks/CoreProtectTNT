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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExplosionListener implements Listener {
    private final Main plugin;

    private static final double INTERACTIVE_EXPLOSION_RADIUS = 5.0;
    private static final double INTERACTIVE_EXPLOSION_ITEM_VELOCITY_MULTIPLIER = 0.8;
    private static final double MACE_PLAYER_SEARCH_RADIUS = 2.0;
    private static final double WIND_CHARGE_MACE_SEARCH_RADIUS = 1.5;

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

    private void handleInteractiveExplosion(List<Block> affectedBlocks, String reason, Location center) {
        for (Block block : affectedBlocks) {
            if (Tag.DOORS.isTagged(block.getType()) || Tag.TRAPDOORS.isTagged(block.getType())) {
                if (block.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP) {
                    continue;
                }
                toggleOpenable(block);
                this.plugin.getApi().logInteraction(reason, block.getLocation());
            }
        }

        for (Item item : center.getWorld().getEntitiesByClass(Item.class, center.getBoundingBox().expand(INTERACTIVE_EXPLOSION_RADIUS))) {
            Vector direction = item.getLocation().toVector().subtract(center.toVector()).normalize();
            item.setVelocity(item.getVelocity().add(direction.multiply(INTERACTIVE_EXPLOSION_ITEM_VELOCITY_MULTIPLIER)));
        }
    }

    private boolean isHoldingMace(Player player) {
        PlayerInventory inventory = player.getInventory();
        return inventory.getItemInMainHand().getType() == Material.MACE || inventory.getItemInOffHand().getType() == Material.MACE;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (e.getBlock().getType() == Material.AIR) {
            Location explosionCenter = e.getBlock().getLocation();
            for (Player player : explosionCenter.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(explosionCenter) < MACE_PLAYER_SEARCH_RADIUS * MACE_PLAYER_SEARCH_RADIUS) {
                    if (isHoldingMace(player)) {
                        List<Block> affectedBlocks = new ArrayList<>(e.blockList());
                        e.blockList().clear();
                        handleInteractiveExplosion(affectedBlocks, "#mace-" + player.getName(), explosionCenter);
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

        if (probablyCauses == null && section.getBoolean("disable-unknown", false)) {
            e.blockList().clear();
            Util.broadcastNearPlayers(location, section.getString("alert"));
            return;
        }

        if (probablyCauses != null) {
            for (Block block : e.blockList()) {
                this.plugin.getApi().logRemoval(probablyCauses, block.getLocation(), block.getType(), block.getBlockData());
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (e.getEntityType() == EntityType.WIND_CHARGE) {
            String shooterName = this.plugin.getCache().getIfPresent(e.getEntity().getUniqueId());
            if (shooterName == null) {
                shooterName = "world";
            }

            boolean isMace = false;
            Location explosionCenter = e.getLocation();

            Player shooter = this.plugin.getServer().getPlayerExact(shooterName);
            if (shooter != null && shooter.getWorld().equals(explosionCenter.getWorld()) &&
                    shooter.getLocation().distanceSquared(explosionCenter) < WIND_CHARGE_MACE_SEARCH_RADIUS * WIND_CHARGE_MACE_SEARCH_RADIUS) {
                if (isHoldingMace(shooter)) {
                    isMace = true;
                }
            }

            String reason = (isMace ? "#mace-" : "#wind_charge-") + shooterName;
            List<Block> affectedBlocks = new ArrayList<>(e.blockList());
            e.blockList().clear();
            handleInteractiveExplosion(affectedBlocks, reason, explosionCenter);
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

        if (track == null) {
            track = this.plugin.getCache().getIfPresent(entity.getLocation());
        }

        if (track == null) {
            if (entity instanceof Creeper creeper) {
                LivingEntity target = creeper.getTarget();
                if (target != null) {
                    track = target.getName();
                }
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
        if (e.getRightClicked().getType() == EntityType.CREEPER && e.getPlayer().getInventory().getItemInMainHand().getType() == Material.FLINT_AND_STEEL) {
            this.plugin.getCache().put(e.getRightClicked().getUniqueId(), "#ignitecreeper-" + e.getPlayer().getName());
        }
    }
}