package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import com.leir4iks.coreprotecttnt.config.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getEntity() instanceof ItemFrame) return;

        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            e.setCancelled(true);
            return;
        }

        if (plugin.getProcessedEntities().getIfPresent(e.getEntity().getUniqueId()) != null) return;

        Config config = plugin.getConfigManager().get();
        if (config.general.debug) {
            logger.info("[Debug] Event: HangingBreakEvent | Entity: " + e.getEntity().getType() + " | Cause: " + e.getCause());
        }

        if (e.getCause() == HangingBreakEvent.RemoveCause.PHYSICS) {
            logHangingRemoval(e.getEntity(), Util.createChainedCause(plugin, "environment", null));
            return;
        }

        if (!config.modules.hanging.enabled) return;
        Location hangingLocation = e.getEntity().getLocation().getBlock().getLocation();
        String reason = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(hangingLocation));
        if (reason == null) {
            if (config.modules.hanging.disableUnknown) {
                e.setCancelled(true);
                Util.broadcastNearPlayers(e.getEntity().getLocation(), config.localization.messages.unknownSourceAlert);
            }
            return;
        }
        String cause = Util.createChainedCause(plugin, e.getCause().name().toLowerCase(Locale.ROOT), reason);
        logHangingRemoval(e.getEntity(), cause);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHangingHit(HangingBreakByEntityEvent e) {
        if (e.getEntity() instanceof ItemFrame) return;

        Hanging hanging = e.getEntity();
        if (plugin.getProcessedEntities().getIfPresent(hanging.getUniqueId()) != null) {
            e.setCancelled(true);
            return;
        }

        plugin.getProcessedEntities().put(hanging.getUniqueId(), true);

        Config config = plugin.getConfigManager().get();
        if (config.general.debug) {
            logger.info("[Debug] Event: HangingBreakByEntityEvent | Entity: " + hanging.getType() + " | Remover: " + (e.getRemover() != null ? e.getRemover().getType() : "null"));
        }

        if (!config.modules.hanging.enabled) return;

        String removerName = getRemoverName(e.getRemover());
        if (removerName == null) {
            if (config.modules.hanging.disableUnknown) {
                e.setCancelled(true);
                Util.broadcastNearPlayers(hanging.getLocation(), config.localization.messages.unknownSourceAlert);
            }
            return;
        }

        logHangingRemoval(hanging, removerName);
    }

    private String getRemoverName(Entity remover) {
        return Optional.ofNullable(remover)
                .map(r -> {
                    if (r instanceof Player) {
                        return r.getName();
                    }
                    String prefix = plugin.getConfigManager().get().formatting.logPrefix;
                    String projectileCause = plugin.getProjectileCache().getIfPresent(r.getUniqueId());
                    if (projectileCause != null) {
                        return projectileCause.startsWith(prefix) ? projectileCause : prefix + projectileCause;
                    }
                    String aggroCause = plugin.getEntityAggroCache().getIfPresent(r.getUniqueId());
                    if (aggroCause != null) {
                        return Util.createChainedCause(plugin, r, aggroCause);
                    }
                    return Util.createChainedCause(plugin, r, null);
                })
                .orElse(null);
    }

    private void logHangingRemoval(Hanging hanging, String reason) {
        if (hanging.getType() != EntityType.PAINTING) return;
        plugin.getApi().logRemoval(reason, hanging.getLocation(), Material.PAINTING, null);
    }
}