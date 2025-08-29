package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

public class FrameListener implements Listener {
    private final Main plugin;
    private final Logger logger;

    public FrameListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFramePlace(HangingPlaceEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame)) return;

        ConfigurationSection section = Util.bakeConfigSection(plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable") || !section.getBoolean("log-placement")) return;

        if (plugin.getConfig().getBoolean("debug")) {
            logger.info("[Debug] ItemFrame placed at " + itemFrame.getLocation() + " by " + e.getPlayer().getName());
        }
        plugin.getApi().logPlacement(e.getPlayer().getName(), itemFrame.getLocation(), itemFrame.getType().isAir() ? Material.ITEM_FRAME : Material.GLOW_ITEM_FRAME, null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractFrame(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame itemFrame)) return;

        ConfigurationSection section = Util.bakeConfigSection(plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable")) return;

        Player player = e.getPlayer();
        ItemStack itemBefore = itemFrame.getItem().clone();
        Rotation rotationBefore = itemFrame.getRotation();

        Main.getScheduler().runTask(() -> {
            ItemStack itemAfter = itemFrame.getItem();
            Rotation rotationAfter = itemFrame.getRotation();

            boolean itemChanged = !itemBefore.isSimilar(itemAfter);
            boolean onlyRotated = !itemChanged && rotationBefore != rotationAfter;

            if (onlyRotated && section.getBoolean("log-rotation")) {
                plugin.getApi().logInteraction("#rotate-" + player.getName(), itemFrame.getLocation());
            } else if (itemChanged && section.getBoolean("log-content-change")) {
                plugin.getApi().logContainerTransaction(player.getName(), itemFrame.getLocation());
            }
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFrameBreakByEntity(HangingBreakByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame)) return;
        handleFrameRemoval(itemFrame, () -> getRemoverName(e.getRemover()), e);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFrameBreakNatural(HangingBreakEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame) || e.getCause() != HangingBreakEvent.RemoveCause.PHYSICS) return;
        ConfigurationSection section = Util.bakeConfigSection(plugin.getConfig(), "itemframe");
        if (!section.getBoolean("log-physics-destruction", true)) return;
        handleFrameRemoval(itemFrame, () -> "#environment", e);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFrameDamageByProjectile(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame) || !(e.getDamager() instanceof Projectile projectile)) return;
        if (plugin.getProcessedEntities().getIfPresent(itemFrame.getUniqueId()) != null) return;
        if (itemFrame.getItem().getType() == Material.AIR || itemFrame.isDead()) return;

        String initiator = plugin.getProjectileCache().getIfPresent(projectile.getUniqueId());
        if (initiator == null) return;

        String reason = initiator.startsWith("#") ? initiator : "#" + initiator;
        plugin.getApi().logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable") || !section.getBoolean("log-piston-destruction")) return;

        for (Block block : e.getBlocks()) {
            handleMechanicalFrameBreak(block, "#piston");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable") || !section.getBoolean("log-piston-destruction")) return;

        for (Block block : e.getBlocks()) {
            handleMechanicalFrameBreak(block, "#piston");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onWaterFlow(BlockFromToEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable") || !section.getBoolean("log-water-destruction")) return;
        handleMechanicalFrameBreak(e.getToBlock(), "#water");
    }

    private void handleMechanicalFrameBreak(Block block, String cause) {
        Collection<ItemFrame> nearbyFrames = block.getLocation().getNearbyEntitiesByType(ItemFrame.class, 0.5);
        for (ItemFrame frame : nearbyFrames) {
            handleFrameRemoval(frame, () -> cause, null);
        }
    }

    private void handleFrameRemoval(ItemFrame frame, java.util.function.Supplier<String> causeSupplier, org.bukkit.event.Cancellable event) {
        if (plugin.getProcessedEntities().getIfPresent(frame.getUniqueId()) != null) {
            if (event != null) event.setCancelled(true);
            return;
        }
        plugin.getProcessedEntities().put(frame.getUniqueId(), true);

        ConfigurationSection section = Util.bakeConfigSection(plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable", true)) return;

        String cause = causeSupplier.get();

        if (cause == null) {
            if (section.getBoolean("disable-unknown")) {
                if (event != null) event.setCancelled(true);
                Util.broadcastNearPlayers(frame.getLocation(), section.getString("alert"));
            }
            return;
        }

        if (plugin.getConfig().getBoolean("debug")) {
            logger.info("[Debug] ItemFrame removed at " + frame.getLocation() + " by " + cause);
        }
        logFrameRemoval(frame, cause);
    }

    private String getRemoverName(Entity remover) {
        return Optional.ofNullable(remover)
                .map(r -> {
                    if (r instanceof Player) {
                        return r.getName();
                    }
                    String projectileCause = plugin.getProjectileCache().getIfPresent(r.getUniqueId());
                    if (projectileCause != null) {
                        return projectileCause.startsWith("#") ? projectileCause : "#" + projectileCause;
                    }
                    String aggroCause = plugin.getEntityAggroCache().getIfPresent(r.getUniqueId());
                    if (aggroCause != null) {
                        return Util.createChainedCause(r, aggroCause);
                    }
                    return "#" + r.getType().name().toLowerCase(Locale.ROOT);
                })
                .orElse(null);
    }

    private void logFrameRemoval(ItemFrame itemFrame, String reason) {
        Material frameMaterial = itemFrame.isVisible() ? (itemFrame.isGlowing() ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME) : Material.ITEM_FRAME;
        plugin.getApi().logRemoval(reason, itemFrame.getLocation(), frameMaterial, null);
        if (itemFrame.getItem().getType() != Material.AIR) {
            plugin.getApi().logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
        }
    }
}