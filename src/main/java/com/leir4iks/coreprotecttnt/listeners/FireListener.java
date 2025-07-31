package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;

import java.util.Locale;

public class FireListener implements Listener {
    private final Main plugin;

    public FireListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "fire");
        if (!section.getBoolean("enable", true)) return;

        String sourceName = null;
        if (e.getPlayer() != null) {
            sourceName = e.getPlayer().getName();
        } else if (e.getIgnitingEntity() != null) {
            sourceName = this.plugin.getCache().getIfPresent(e.getIgnitingEntity());
            if (sourceName == null) {
                sourceName = "#" + e.getIgnitingEntity().getType().name().toLowerCase(Locale.ROOT);
            }
        } else if (e.getIgnitingBlock() != null) {
            sourceName = this.plugin.getCache().getIfPresent(e.getIgnitingBlock().getLocation());
        }

        if (sourceName != null) {
            String reason = sourceName.startsWith("#") ? sourceName : "#fire-" + sourceName;
            this.plugin.getCache().put(e.getBlock().getLocation(), reason);
        } else if (section.getBoolean("disable-unknown", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "fire");
        if (!section.getBoolean("enable", true)) return;
        if (e.getIgnitingBlock() == null) return;

        String sourceFromCache = this.plugin.getCache().getIfPresent(e.getIgnitingBlock().getLocation());
        if (sourceFromCache != null) {
            this.plugin.getCache().put(e.getBlock().getLocation(), sourceFromCache);
            this.plugin.getCache().put(e.getIgnitingBlock().getLocation(), sourceFromCache);
            String reason = sourceFromCache.startsWith("#") ? sourceFromCache : "#burn-" + sourceFromCache;
            this.plugin.getApi().logRemoval(reason, e.getBlock().getLocation(), e.getBlock().getType(), e.getBlock().getBlockData());
        } else if (section.getBoolean("disable-unknown", true)) {
            e.setCancelled(true);
            Util.broadcastNearPlayers(e.getIgnitingBlock().getLocation(), section.getString("alert"));
        }
    }
}