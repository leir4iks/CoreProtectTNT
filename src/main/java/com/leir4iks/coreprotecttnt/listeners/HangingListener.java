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
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

public class HangingListener implements Listener {

    private final Main plugin;

    public HangingListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getEntity() instanceof ItemFrame frame) {
            Material frameMaterial = (frame.getType() == EntityType.GLOW_ITEM_FRAME) ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME;
            plugin.getApi().logPlacement(e.getPlayer().getName(), frame.getLocation(), frameMaterial, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameInteract(PlayerInteractEntityEvent e) {
        if (e.isCancelled() || !(e.getRightClicked() instanceof ItemFrame frame)) return;

        Player player = e.getPlayer();
        ItemStack itemBefore = frame.getItem().clone();
        Rotation rotationBefore = frame.getRotation();

        Main.getScheduler().runTask(frame, () -> {
            if (frame.isDead()) return;
            ItemStack itemAfter = frame.getItem();
            Rotation rotationAfter = frame.getRotation();

            if (itemBefore.isSimilar(itemAfter) && rotationBefore == rotationAfter) return;

            if (itemBefore.isSimilar(itemAfter) && rotationBefore != rotationAfter) {
                if (getSectionForEntity(frame).getBoolean("log-rotation", true)) {
                    plugin.getApi().logInteraction("#rotate-" + player.getName(), frame.getLocation());
                }
                return;
            }

            if (!itemBefore.isSimilar(itemAfter)) {
                if (getSectionForEntity(frame).getBoolean("log-content-change", true)) {
                    plugin.getCoreProtectHook().logFrameTransaction(player, frame, itemBefore, itemAfter);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Hanging hanging) {
            String cause = getRemoverName(e.getDamager());
            handleHangingRemoval(hanging, cause, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (!(e.getEntity() instanceof Hanging hanging)) return;
        if (plugin.getProcessedEntities().getIfPresent(hanging.getUniqueId()) != null) return;

        if (e.getCause() == RemoveCause.EXPLOSION) {
            e.setCancelled(true);
            return;
        }

        if (hanging instanceof ItemFrame) {
            String cause = (e instanceof HangingBreakByEntityEvent entityEvent) ? getRemoverName(entityEvent.getRemover()) : "#" + e.getCause().name().toLowerCase(Locale.ROOT);
            handleHangingRemoval(hanging, cause, e);
        } else {
            String cause;
            if (e.getCause() == RemoveCause.PHYSICS) {
                cause = "#environment";
            } else {
                Location hangingLocation = hanging.getLocation().getBlock().getLocation();
                String reason = plugin.getBlockPlaceCache().getIfPresent(hangingLocation);
                if (reason == null) {
                    if (getSectionForEntity(hanging).getBoolean("disable-unknown")) {
                        e.setCancelled(true);
                        Util.broadcastNearPlayers(hanging.getLocation(), getSectionForEntity(hanging).getString("alert"));
                    }
                    return;
                }
                cause = "#" + e.getCause().name().toLowerCase(Locale.ROOT) + "-" + reason;
            }
            handleHangingRemoval(hanging, cause, e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block block : e.getBlocks()) {
            handleMechanicalBreak(block.getRelative(e.getDirection()), "#piston");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!e.isSticky()) return;
        for (Block block : e.getBlocks()) {
            handleMechanicalBreak(block.getRelative(e.getDirection()), "#piston");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWaterFlow(BlockFromToEvent e) {
        handleMechanicalBreak(e.getToBlock(), "#water");
    }

    private void handleMechanicalBreak(Block block, String cause) {
        block.getLocation().getNearbyEntitiesByType(Hanging.class, 1.5).forEach(hanging -> {
            if (hanging.getLocation().getBlock().equals(block)) {
                handleHangingRemoval(hanging, cause, null);
            }
        });
    }

    private void handleHangingRemoval(Hanging hanging, String cause, org.bukkit.event.Cancellable event) {
        if (plugin.getProcessedEntities().getIfPresent(hanging.getUniqueId()) != null) return;
        plugin.getProcessedEntities().put(hanging.getUniqueId(), true);

        ConfigurationSection section = getSectionForEntity(hanging);
        if (!section.getBoolean("enable", true)) return;

        if (cause == null) {
            if (section.getBoolean("disable-unknown", false)) {
                if (event != null) event.setCancelled(true);
                Util.broadcastNearPlayers(hanging.getLocation(), section.getString("alert"));
            }
            return;
        }

        if (hanging instanceof ItemFrame frame) {
            Material frameMaterial = frame.isGlowing() ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME;
            plugin.getApi().logRemoval(cause, frame.getLocation(), frameMaterial, null);
            if (frame.getItem().getType() != Material.AIR) {
                plugin.getApi().logRemoval(cause, frame.getLocation(), frame.getItem().getType(), null);
            }
        } else if (hanging.getType() == EntityType.PAINTING) {
            plugin.getApi().logRemoval(cause, hanging.getLocation(), Material.PAINTING, null);
        }
    }

    private String getRemoverName(Entity remover) {
        return Optional.ofNullable(remover)
                .map(r -> {
                    if (r instanceof Player) return r.getName();
                    String projectileCause = plugin.getProjectileCache().getIfPresent(r.getUniqueId());
                    if (projectileCause != null) return projectileCause;
                    String aggroCause = plugin.getEntityAggroCache().getIfPresent(r.getUniqueId());
                    if (aggroCause != null) return Util.createChainedCause(r, aggroCause);
                    if (r.getType() == EntityType.ENDER_DRAGON) return "#ender_dragon";
                    return "#" + r.getType().name().toLowerCase(Locale.ROOT);
                })
                .orElse(null);
    }

    private ConfigurationSection getSectionForEntity(Hanging hanging) {
        String path = (hanging instanceof ItemFrame) ? "itemframe" : "hanging";
        return Util.bakeConfigSection(plugin.getConfig(), path);
    }
}