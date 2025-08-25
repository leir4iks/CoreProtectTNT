package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
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
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Event: PlayerInteractEntityEvent | Player: " + e.getPlayer().getName() + " | Clicked: " + e.getRightClicked().getType());
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable", true)) return;

        Player player = e.getPlayer();
        String playerName = player.getName();
        boolean hasItem = itemFrame.getItem().getType() != Material.AIR;
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        boolean isPuttingItem = !mainHandItem.getType().isAir();

        if (!hasItem && isPuttingItem) {
            plugin.getApi().logPlacement("#additem-" + playerName, itemFrame.getLocation(), mainHandItem.getType(), mainHandItem.getData());
        } else if (hasItem && !isPuttingItem) {
            plugin.getApi().logRemoval(playerName, itemFrame.getLocation(), itemFrame.getItem().getType(), itemFrame.getItem().getData());
        } else if (hasItem) {
            plugin.getApi().logInteraction("#rotate-" + playerName, itemFrame.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.ENTITY || e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) return;
        if (plugin.getProcessedEntities().getIfPresent(e.getEntity().getUniqueId()) != null) return;
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Event: HangingBreakEvent | Entity: " + e.getEntity().getType() + " | Cause: " + e.getCause());
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "hanging");
        if (!section.getBoolean("enable", true)) return;
        Location hangingLocation = e.getEntity().getLocation().getBlock().getLocation();
        String reason = this.plugin.getCache().getIfPresent(hangingLocation);
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHangingHit(HangingBreakByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame) && !(e.getEntity() instanceof Painting)) return;
        if (plugin.getProcessedEntities().getIfPresent(e.getEntity().getUniqueId()) != null) return;
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[Debug] Event: HangingBreakByEntityEvent | Entity: " + e.getEntity().getType() + " | Remover: " + (e.getRemover() != null ? e.getRemover().getType() : "null"));
        }

        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), e.getEntity() instanceof ItemFrame ? "itemframe" : "hanging");
        if (!section.getBoolean("enable", true)) return;
        Hanging hanging = e.getEntity();
        String removerName = null;

        if (e.getRemover() instanceof Player) {
            removerName = e.getRemover().getName();
        } else if (e.getRemover() != null) {
            removerName = plugin.getCache().getIfPresent(e.getRemover().getUniqueId());
            if (removerName == null) {
                removerName = "#" + e.getRemover().getType().name().toLowerCase(Locale.ROOT);
            }
        }

        if (removerName == null) {
            if (section.getBoolean("disable-unknown")) {
                e.setCancelled(true);
                Util.broadcastNearPlayers(hanging.getLocation(), section.getString("alert"));
            }
            return;
        }

        String reason = removerName.startsWith("#") ? removerName : "#" + Util.getRootCause(removerName);
        logHangingRemoval(hanging, reason);
    }

    private void logHangingRemoval(Hanging hanging, String reason) {
        Material material = hanging.getType() == EntityType.ITEM_FRAME ? Material.ITEM_FRAME : Material.PAINTING;
        plugin.getApi().logRemoval(reason, hanging.getLocation(), material, null);
        if (hanging instanceof ItemFrame itemFrame) {
            if (itemFrame.getItem() != null && itemFrame.getItem().getType() != Material.AIR) {
                plugin.getApi().logRemoval(reason, hanging.getLocation(), itemFrame.getItem().getType(), null);
            }
        }
    }
}