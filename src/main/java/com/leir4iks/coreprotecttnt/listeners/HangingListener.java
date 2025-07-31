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

public class HangingListener implements Listener {
    private final Main plugin;

    public HangingListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClickItemFrame(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame)) return;
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "itemframe");
        if (!section.getBoolean("enable", true)) return;
        ItemFrame itemFrame = (ItemFrame) e.getRightClicked();
        Player player = e.getPlayer();
        String playerName = player.getName();
        this.plugin.getApi().logInteraction(playerName, itemFrame.getLocation());
        boolean hasItem = itemFrame.getItem().getType() != Material.AIR;
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        boolean placingItem = !mainHandItem.getType().isAir() || !offHandItem.getType().isAir();
        if (!hasItem && placingItem) {
            ItemStack putIn = !mainHandItem.getType().isAir() ? mainHandItem : offHandItem;
            this.plugin.getApi().logPlacement("#additem-" + playerName, itemFrame.getLocation(), putIn.getType(), null);
        } else if (hasItem) {
            this.plugin.getApi().logRemoval("#rotate-" + playerName, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
            this.plugin.getApi().logPlacement("#rotate-" + playerName, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.ENTITY) return;
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
        String materialName = e.getEntity().getType().name();
        Material material = Material.matchMaterial(materialName);
        String cause = reason.startsWith("#") ? reason : "#" + e.getCause().name() + "-" + reason;
        if (material != null) {
            this.plugin.getApi().logRemoval(cause, hangingLocation, material, null);
        } else {
            this.plugin.getApi().logInteraction(cause, hangingLocation);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHangingHit(HangingBreakByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame) && !(e.getEntity() instanceof Painting)) return;
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), e.getEntity() instanceof ItemFrame ? "itemframe" : "hanging");
        if (!section.getBoolean("enable", true)) return;
        Hanging hanging = e.getEntity();
        String removerName = null;
        if (e.getRemover() instanceof Player) {
            removerName = e.getRemover().getName();
        } else if (e.getRemover() != null) {
            removerName = plugin.getCache().getIfPresent(e.getRemover());
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
        String reason = removerName.startsWith("#") ? removerName : "#" + hanging.getType().name().toLowerCase(Locale.ROOT) + "-" + removerName;
        Material material = hanging.getType() == EntityType.ITEM_FRAME ? Material.ITEM_FRAME : Material.PAINTING;
        plugin.getApi().logRemoval(reason, hanging.getLocation(), material, null);
        if (hanging instanceof ItemFrame) {
            ItemFrame itemFrame = (ItemFrame) hanging;
            if (itemFrame.getItem() != null && itemFrame.getItem().getType() != Material.AIR) {
                plugin.getApi().logRemoval(reason, hanging.getLocation(), itemFrame.getItem().getType(), null);
            }
        }
    }
}