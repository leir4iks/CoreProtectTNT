package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

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
    public void onPlayerInteractFrame(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame itemFrame)) return;

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable", true)) return;

        Player player = e.getPlayer();
        ItemStack itemBefore = itemFrame.getItem().clone();
        Rotation rotationBefore = itemFrame.getRotation();

        Main.getScheduler().runTask(() -> {
            ItemStack itemAfter = itemFrame.getItem();
            Rotation rotationAfter = itemFrame.getRotation();

            boolean itemAddedOrRemoved = !itemBefore.isSimilar(itemAfter);
            boolean onlyRotated = !itemAddedOrRemoved && rotationBefore != rotationAfter;

            if (onlyRotated) {
                plugin.getApi().logInteraction("#rotate-" + player.getName(), itemFrame.getLocation());
            } else if (itemAddedOrRemoved) {
                plugin.getApi().logContainerTransaction(player.getName(), itemFrame.getLocation());
            }
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onItemFrameBreakByEntity(HangingBreakByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame)) return;

        if (plugin.getProcessedEntities().getIfPresent(itemFrame.getUniqueId()) != null) {
            e.setCancelled(true);
            return;
        }
        plugin.getProcessedEntities().put(itemFrame.getUniqueId(), true);

        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Event: HangingBreakByEntityEvent | Entity: " + itemFrame.getType() + " | Remover: " + (e.getRemover() != null ? e.getRemover().getType() : "null"));
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable", true)) return;

        String removerName = getRemoverName(e.getRemover());
        if (removerName == null) {
            if (section.getBoolean("disable-unknown")) {
                e.setCancelled(true);
                Util.broadcastNearPlayers(itemFrame.getLocation(), section.getString("alert"));
            }
            return;
        }

        logFrameRemoval(itemFrame, removerName);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onItemFrameDamageByProjectile(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame) || !(e.getDamager() instanceof Projectile projectile)) return;
        if (plugin.getProcessedEntities().getIfPresent(itemFrame.getUniqueId()) != null) return;
        if (itemFrame.getItem().getType() == Material.AIR || itemFrame.isDead()) return;

        String initiator = plugin.getProjectileCache().getIfPresent(projectile.getUniqueId());
        if (initiator == null) return;

        String reason = initiator.startsWith("#") ? initiator : "#" + initiator;
        plugin.getApi().logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFrameExplodeByEntity(EntityExplodeEvent e) {
        if (e.getYield() == 0.0f) return;
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable", true)) return;

        String track = Util.getEntityExplosionCause(e.getEntity(), plugin);
        if (track == null) return;

        String entityName = e.getEntityType().name().toLowerCase(Locale.ROOT);
        String reason = "#" + entityName + "-" + Util.getRootCause(track);
        handleFramesInExplosion(e.getLocation(), e.getYield(), reason);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFrameExplodeByBlock(BlockExplodeEvent e) {
        if (e.getYield() == 0.0f) return;
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable", true)) return;

        String initiator = this.plugin.getBlockPlaceCache().getIfPresent(e.getBlock().getLocation());
        if (initiator == null) return;

        handleFramesInExplosion(e.getBlock().getLocation(), e.getYield(), initiator);
    }

    private void handleFramesInExplosion(Location center, float yield, String reason) {
        double radius = Math.max(yield, 5.0f);
        Collection<ItemFrame> itemFrames = center.getWorld().getNearbyEntities(center, radius, radius, radius, entity -> entity instanceof ItemFrame)
                .stream()
                .map(ItemFrame.class::cast)
                .toList();

        for (ItemFrame frame : itemFrames) {
            if (frame.isDead()) continue;
            if (plugin.getProcessedEntities().getIfPresent(frame.getUniqueId()) != null) continue;

            plugin.getProcessedEntities().put(frame.getUniqueId(), true);

            logFrameRemoval(frame, reason);
            frame.remove();
        }
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
                        return "#" + r.getType().name().toLowerCase(Locale.ROOT) + "-" + aggroCause;
                    }
                    return "#" + r.getType().name().toLowerCase(Locale.ROOT);
                })
                .orElse(null);
    }

    private void logFrameRemoval(ItemFrame itemFrame, String reason) {
        plugin.getApi().logRemoval(reason, itemFrame.getLocation(), Material.ITEM_FRAME, null);
        if (itemFrame.getItem().getType() != Material.AIR) {
            plugin.getApi().logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
        }
    }
}