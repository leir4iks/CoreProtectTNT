package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FireListener implements Listener {
    private final Main plugin;
    private final Map<Location, String> fireTracker = new ConcurrentHashMap<>();

    public FireListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "fire");
        if (!section.getBoolean("enable", true)) {
            return;
        }

        String cause = null;
        if (e.getPlayer() != null) {
            cause = e.getPlayer().getName();
        } else if (e.getIgnitingEntity() != null) {
            cause = this.plugin.getCache().getIfPresent(e.getIgnitingEntity());
            if (cause == null) {
                cause = "#" + e.getIgnitingEntity().getType().name().toLowerCase(Locale.ROOT);
            }
        } else if (e.getIgnitingBlock() != null) {
            cause = this.fireTracker.get(e.getIgnitingBlock().getLocation());
            if (cause == null) {
                cause = this.plugin.getCache().getIfPresent(e.getIgnitingBlock().getLocation());
            }
        }

        if (cause != null) {
            String reason = cause.startsWith("#") ? cause : "#fire-" + cause;
            this.fireTracker.put(e.getBlock().getLocation(), reason);
        } else if (section.getBoolean("disable-unknown", false)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(this.plugin.getConfig(), "fire");
        if (!section.getBoolean("enable", true) || e.getIgnitingBlock() == null) {
            return;
        }

        String source = this.fireTracker.get(e.getIgnitingBlock().getLocation());
        if (source == null) {
            source = this.plugin.getCache().getIfPresent(e.getIgnitingBlock().getLocation());
        }

        if (source != null) {
            this.fireTracker.put(e.getBlock().getLocation(), source);
            String reason = source.startsWith("#") ? source : "#fire-" + source;
            this.plugin.getApi().logRemoval(reason, e.getBlock().getLocation(), e.getBlock().getType(), e.getBlock().getBlockData());
        } else if (section.getBoolean("disable-unknown", false)) {
            e.setCancelled(true);
            Util.broadcastNearPlayers(e.getBlock().getLocation(), section.getString("alert"));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFireFade(BlockFadeEvent e) {
        if (e.getBlock().getType() == Material.FIRE) {
            this.fireTracker.remove(e.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent e) {
        this.fireTracker.remove(e.getBlock().getLocation());
    }
}