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
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class ExplosionListener implements Listener {
    private final Main plugin;
    private final Logger logger;

    public ExplosionListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
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

        BoundingBox searchBox = BoundingBox.of(center, 5.0, 5.0, 5.0);
        for (Entity entity : center.getWorld().getNearbyEntities(searchBox)) {
            if (entity instanceof Item item) {
                Vector direction = item.getLocation().toVector().subtract(center.toVector()).normalize();
                item.setVelocity(item.getVelocity().add(direction.multiply(0.8)));
            }
        }
    }

    private boolean isHoldingMace(Player player) {
        PlayerInventory inventory = player.getInventory();
        return inventory.getItemInMainHand().getType() == Material.MACE || inventory.getItemInOffHand().getType() == Material.MACE;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof Mob mob)) return;

        String cause = this.plugin.getCache().getIfPresent(mob.getUniqueId());
        if (cause == null && mob.getTarget() instanceof Player target) {
            cause = target.getName();
        }

        if (cause != null) {
            String reason = "#" + mob.getType().name().toLowerCase(Locale.ROOT) + "-" + cause;
            this.plugin.getApi().logRemoval(reason, e.getBlock().getLocation(), e.getBlock().getType(), e.getBlock().getBlockData());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent e) {
        boolean isDebug = plugin.getConfig().getBoolean("debug", false);
        if (isDebug) {
            logger.info("[Debug] Event: BlockExplodeEvent | Block: " + e.getBlock().getType() + " | Yield: " + e.getYield());
        }

        if (e.getBlock().getType() == Material.AIR) {
            Location explosionCenter = e.getBlock().getLocation();
            for (Player player : explosionCenter.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(explosionCenter) < 4.0) {
                    if (isHoldingMace(player)) {
                        if (isDebug) logger.info("[Debug] Cause: Mace ground smash by " + player.getName() + ". Processing interactions.");
                        List<Block> affectedBlocks = new ArrayList<>(e.blockList());
                        e.blockList().clear();
                        handleInteractiveExplosion(affectedBlocks, "#mace-" + player.getName(), explosionCenter);
                        return;
                    }
                }
            }
        }

        if (e.getYield() == 0.0f) {
            if (isDebug) logger.info("[Debug] Cause: Zero yield explosion. Ignoring.");
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
            if (isDebug) logger.info("[Debug] Logging block explosion removal caused by: " + probablyCauses);
            for (Block block : e.blockList()) {
                this.plugin.getApi().logRemoval(probablyCauses, block.getLocation(), block.getType(), block.getBlockData());
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (e.getYield() == 0.0f || e.blockList().isEmpty()) {
            return;
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "entity-explosion");
        if (!section.getBoolean("enable", true)) return;

        Entity entity = e.getEntity();
        String track = this.plugin.getCache().getIfPresent(entity.getUniqueId());

        if (track == null) {
            if (entity instanceof Creeper creeper && creeper.getTarget() != null) {
                track = creeper.getTarget().getName();
            } else if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent event) {
                Entity damager = event.getDamager();
                String damagerTrack = this.plugin.getCache().getIfPresent(damager.getUniqueId());
                if (damagerTrack != null) {
                    track = Util.createChainedCause(damager, damagerTrack);
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

        String rootCause = Util.getRootCause(track);
        String reason = "#" + e.getEntityType().name().toLowerCase(Locale.ROOT) + "-" + rootCause;

        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Logging entity explosion removal caused by: " + reason + " (Full chain: " + track + ")");
        }

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

        if (clickedBlock.getBlockData() instanceof Bed) {
            this.plugin.getCache().put(clickedBlock.getLocation(), "#bed-" + e.getPlayer().getName());
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