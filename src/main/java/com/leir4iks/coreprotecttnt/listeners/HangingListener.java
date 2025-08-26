package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

public class HangingListener implements Listener {
    private final Main plugin;
    private final Logger logger;

    public HangingListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClickItemFrame(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame itemFrame)) return;

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable", true)) return;

        Player player = e.getPlayer();
        ItemStack itemBefore = itemFrame.getItem().clone();
        Rotation rotationBefore = itemFrame.getRotation();

        Bukkit.getScheduler().runTask(plugin, () -> {
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
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            e.setCancelled(true);
            return;
        }

        if (plugin.getProcessedEntities().getIfPresent(e.getEntity().getUniqueId()) != null) return;
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Event: HangingBreakEvent | Entity: " + e.getEntity().getType() + " | Cause: " + e.getCause());
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "hanging");
        if (!section.getBoolean("enable", true)) return;
        Location hangingLocation = e.getEntity().getLocation().getBlock().getLocation();
        String reason = this.plugin.getBlockPlaceCache().getIfPresent(hangingLocation);
        if (reason == null) {
            if (section.getBoolean("disable-unknown")) {
                e.setCancelled(true);
                Util.broadcastNearPlayers(e.getEntity().getLocation(), section.getString("alert"));
            }
            return;
        }
        String cause = "#" + e.getCause().name().toLowerCase(Locale.ROOT) + "-" + reason;
        logHangingRemoval(e.getEntity(), cause);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHangingHit(HangingBreakByEntityEvent e) {
        Hanging hanging = e.getEntity();
        if (plugin.getProcessedEntities().getIfPresent(hanging.getUniqueId()) != null) {
            e.setCancelled(true);
            return;
        }

        plugin.getProcessedEntities().put(hanging.getUniqueId(), true);

        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Event: HangingBreakByEntityEvent | Entity: " + hanging.getType() + " | Remover: " + (e.getRemover() != null ? e.getRemover().getType() : "null"));
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), hanging instanceof ItemFrame ? "itemframe" : "hanging");
        if (!section.getBoolean("enable", true)) return;

        String removerName = getRemoverName(e.getRemover());
        if (removerName == null) {
            if (section.getBoolean("disable-unknown")) {
                e.setCancelled(true);
                Util.broadcastNearPlayers(hanging.getLocation(), section.getString("alert"));
            }
            return;
        }

        logHangingRemoval(hanging, removerName);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onItemFrameDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame) || !(e.getDamager() instanceof Projectile projectile)) return;
        if (plugin.getProcessedEntities().getIfPresent(itemFrame.getUniqueId()) != null) return;
        if (itemFrame.getItem().getType() == Material.AIR || itemFrame.isDead()) return;

        String initiator = plugin.getProjectileCache().getIfPresent(projectile.getUniqueId());
        if (initiator == null) return;

        String reason = initiator.startsWith("#") ? initiator : "#" + initiator;
        plugin.getApi().logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
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

    private void logHangingRemoval(Hanging hanging, String reason) {
        Material material = hanging.getType() == EntityType.ITEM_FRAME ? Material.ITEM_FRAME : Material.PAINTING;
        plugin.getApi().logRemoval(reason, hanging.getLocation(), material, null);
        if (hanging instanceof ItemFrame itemFrame) {
            if (itemFrame.getItem().getType() != Material.AIR) {
                plugin.getApi().logRemoval(reason, hanging.getLocation(), itemFrame.getItem().getType(), null);
            }
        }
    }
}