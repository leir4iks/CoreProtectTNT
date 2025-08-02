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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class ExplosionListener implements Listener {
    private final Main plugin;
    private final Logger logger;

    private static final double INTERACTIVE_EXPLOSION_RADIUS = 5.0;
    private static final double INTERACTIVE_EXPLOSION_ITEM_VELOCITY_MULTIPLIER = 0.8;
    private static final double MACE_PLAYER_SEARCH_RADIUS = 2.0;
    private static final double WIND_CHARGE_MACE_SEARCH_RADIUS = 1.5;

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

        BoundingBox searchBox = BoundingBox.of(center, INTERACTIVE_EXPLOSION_RADIUS, INTERACTIVE_EXPLOSION_RADIUS, INTERACTIVE_EXPLOSION_RADIUS);
        for (Entity entity : center.getWorld().getNearbyEntities(searchBox)) {
            if (entity instanceof Item item) {
                Vector direction = item.getLocation().toVector().subtract(center.toVector()).normalize();
                item.setVelocity(item.getVelocity().add(direction.multiply(INTERACTIVE_EXPLOSION_ITEM_VELOCITY_MULTIPLIER)));
            }
        }
    }

    private boolean isHoldingMace(Player player) {
        PlayerInventory inventory = player.getInventory();
        return inventory.getItemInMainHand().getType() == Material.MACE || inventory.getItemInOffHand().getType() == Material.MACE;
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
                if (player.getLocation().distanceSquared(explosionCenter) < MACE_PLAYER_SEARCH_RADIUS * MACE_PLAYER_SEARCH_RADIUS) {
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
        boolean isDebug = plugin.getConfig().getBoolean("debug", false);
        if (isDebug) {
            logger.info("[Debug] Event: EntityExplodeEvent | Entity: " + e.getEntityType().name() + " | Yield: " + e.getYield());
        }

        if (e.getEntityType() == EntityType.WIND_CHARGE) {
            String shooterName = this.plugin.getCache().getIfPresent(e.getEntity().getUniqueId());
            if (shooterName == null) shooterName = "world";

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
            if (isDebug) logger.info("[Debug] Cause: " + (isMace ? "Mace entity smash" : "Wind Charge") + " from " + shooterName + ". Processing interactions.");

            List<Block> affectedBlocks = new ArrayList<>(e.blockList());
            e.blockList().clear();
            handleInteractiveExplosion(affectedBlocks, reason, explosionCenter);
            return;
        }

        if (e.getYield() == 0.0f) {
            if (isDebug) logger.info("[Debug] Cause: Zero yield explosion. Ignoring.");
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
            if (entity instanceof Creeper creeper) {
                LivingEntity target = creeper.getTarget();
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
        if (isDebug) logger.info("[Debug] Logging entity explosion removal caused by: " + reason);
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