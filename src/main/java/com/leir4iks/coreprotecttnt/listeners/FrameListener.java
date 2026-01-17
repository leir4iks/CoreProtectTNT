package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import com.leir4iks.coreprotecttnt.config.Config;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
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
import org.bukkit.projectiles.ProjectileSource;

import java.util.Collection;
import java.util.Locale;
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
        if (e.getPlayer() == null) return;

        Config config = plugin.getConfigManager().get();
        if (!config.modules.itemFrames.enabled || !config.modules.itemFrames.logPlacement) return;

        if (config.general.debug) {
            logger.info("[Debug] ItemFrame placed at " + itemFrame.getLocation() + " by " + e.getPlayer().getName());
        }
        Material frameMaterial = (itemFrame.getType() == EntityType.GLOW_ITEM_FRAME) ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME;
        plugin.getApi().logPlacement(e.getPlayer().getName(), itemFrame.getLocation(), frameMaterial, null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractFrame(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame itemFrame)) return;

        Config config = plugin.getConfigManager().get();
        if (!config.modules.itemFrames.enabled) return;

        Player player = e.getPlayer();
        ItemStack itemBefore = itemFrame.getItem().clone();
        Rotation rotationBefore = itemFrame.getRotation();

        boolean debug = config.general.debug;
        if (debug) {
            logger.info("[Debug] PlayerInteractFrame: Player " + player.getName() + " interacted with frame at " + itemFrame.getLocation());
        }

        Main.getScheduler().runTask(itemFrame, () -> {
            if (!itemFrame.isValid()) return;

            ItemStack itemAfter = itemFrame.getItem();
            Rotation rotationAfter = itemFrame.getRotation();

            boolean itemChanged = !itemBefore.isSimilar(itemAfter);
            boolean onlyRotated = !itemChanged && rotationBefore != rotationAfter;

            if (onlyRotated && config.modules.itemFrames.logRotation) {
                String reason = Util.createChainedCause(plugin, "rotate", player.getName());
                if (debug) logger.info("[Debug] Frame rotated. Logging interaction: " + reason);
                plugin.getApi().logInteraction(reason, itemFrame.getLocation());
            } else if (itemChanged && config.modules.itemFrames.logContentChange) {
                if (debug) logger.info("[Debug] Frame content changed. Logging container transaction for: " + player.getName());
                plugin.getApi().logContainerTransaction(player.getName(), itemFrame.getLocation());
            }
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFrameBreakByEntity(HangingBreakByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame)) return;

        if (plugin.getConfigManager().get().general.debug) {
            logger.info("[Debug] onFrameBreakByEntity triggered. Remover: " + (e.getRemover() != null ? e.getRemover().getType() : "null"));
        }

        handleFrameRemoval(itemFrame, () -> resolveRemover(e.getRemover()), e);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFrameBreakNatural(HangingBreakEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame)) return;
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) return;

        if (e.getCause() == HangingBreakEvent.RemoveCause.PHYSICS) {
            Config config = plugin.getConfigManager().get();
            if (!config.modules.itemFrames.logPhysicsDestruction) return;
            handleFrameRemoval(itemFrame, () -> Util.createChainedCause(plugin, "environment", null), e);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFrameDamageByProjectile(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame itemFrame)) return;

        boolean debug = plugin.getConfigManager().get().general.debug;

        if (debug) {
            logger.info("[Debug] onFrameDamageByProjectile triggered. Damager: " + e.getDamager().getType());
        }

        Entity damager = e.getDamager();

        if (itemFrame.getItem().getType() == Material.AIR || itemFrame.isDead()) return;
        if (plugin.getProcessedEntities().getIfPresent(itemFrame.getUniqueId()) != null) return;

        String reason = resolveRemover(damager);

        if (debug) {
            logger.info("[Debug] Resolved reason for item popping: " + reason);
        }

        if (reason == null) return;

        plugin.getApi().logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Config config = plugin.getConfigManager().get();
        if (!config.modules.itemFrames.enabled || !config.modules.itemFrames.logPistonDestruction) return;

        String cause = getPistonCause(e.getBlock());
        for (Block block : e.getBlocks()) {
            handleMechanicalFrameBreak(block, cause);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Config config = plugin.getConfigManager().get();
        if (!config.modules.itemFrames.enabled || !config.modules.itemFrames.logPistonDestruction) return;

        String cause = getPistonCause(e.getBlock());
        for (Block block : e.getBlocks()) {
            handleMechanicalFrameBreak(block, cause);
        }
    }

    private String getPistonCause(Block block) {
        String pistonPlacer = plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(block.getLocation()));
        return (pistonPlacer != null)
                ? Util.createChainedCause(plugin, "piston", pistonPlacer)
                : Util.createChainedCause(plugin, "piston", null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onWaterFlow(BlockFromToEvent e) {
        Config config = plugin.getConfigManager().get();
        if (!config.modules.itemFrames.enabled || !config.modules.itemFrames.logWaterDestruction) return;
        handleMechanicalFrameBreak(e.getToBlock(), Util.createChainedCause(plugin, "water", null));
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

        Config config = plugin.getConfigManager().get();
        if (!config.modules.itemFrames.enabled) return;

        String cause = causeSupplier.get();

        if (cause == null) {
            if (config.modules.itemFrames.disableUnknown) {
                if (event != null) event.setCancelled(true);
                Util.broadcastNearPlayers(frame.getLocation(), config.localization.messages.unknownSourceAlert);
            }
            return;
        }

        if (config.general.debug) {
            logger.info("[Debug] ItemFrame removed at " + frame.getLocation() + " by " + cause);
        }
        logFrameRemoval(frame, cause);
    }

    private String resolveRemover(Entity entity) {
        boolean debug = plugin.getConfigManager().get().general.debug;

        if (entity == null) {
            if (debug) logger.info("[Debug] resolveRemover: Entity is null");
            return null;
        }

        if (debug) logger.info("[Debug] resolveRemover: Processing entity " + entity.getType() + " (" + entity.getUniqueId() + ")");

        if (entity instanceof Player p) {
            String projectileChain = plugin.getPlayerProjectileCache().getIfPresent(p.getUniqueId());
            if (projectileChain != null) {
                if (debug) logger.info("[Debug] resolveRemover: Found recent projectile for player: " + projectileChain);
                return projectileChain;
            }
            return p.getName();
        }

        String separator = plugin.getConfigManager().get().formatting.causeSeparator;
        String cached = plugin.getProjectileCache().getIfPresent(entity.getUniqueId());

        if (debug) logger.info("[Debug] resolveRemover: Cache lookup result: " + cached);

        if (cached != null) {
            if (cached.contains(separator)) {
                String result = Util.addPrefixIfNeeded(plugin, cached);
                if (debug) logger.info("[Debug] resolveRemover: Returning cached chain: " + result);
                return result;
            } else {
                String result;
                if (entity instanceof Projectile projectile) {
                    String projectileName = projectile.getType().name().toLowerCase(Locale.ROOT);
                    result = Util.createChainedCause(plugin, projectileName, cached);
                } else if (entity instanceof TNTPrimed) {
                    result = Util.createChainedCause(plugin, "tnt", cached);
                } else if (entity instanceof ExplosiveMinecart) {
                    result = Util.createChainedCause(plugin, "tnt_minecart", cached);
                } else if (entity instanceof Creeper) {
                    result = Util.createChainedCause(plugin, "creeper", cached);
                } else {
                    result = Util.addPrefixIfNeeded(plugin, cached);
                }

                if (debug) logger.info("[Debug] resolveRemover: Built chain from cache + entity type: " + result);
                return result;
            }
        }

        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            String projectileName = projectile.getType().name().toLowerCase(Locale.ROOT);

            if (debug) logger.info("[Debug] resolveRemover: Cache miss. Checking shooter: " + (shooter != null ? shooter.getClass().getSimpleName() : "null"));

            if (shooter instanceof Player p) {
                String result = Util.createChainedCause(plugin, projectileName, p.getName());
                if (debug) logger.info("[Debug] resolveRemover: Built chain from Player shooter: " + result);
                return result;
            } else if (shooter instanceof Mob mob) {
                String aggro = plugin.getEntityAggroCache().getIfPresent(mob.getUniqueId());
                String result = Util.createChainedCause(plugin, projectileName, Util.createChainedCause(plugin, mob, aggro));
                if (debug) logger.info("[Debug] resolveRemover: Built chain from Mob shooter. Aggro: " + aggro + " Result: " + result);
                return result;
            }
        }

        String aggro = plugin.getEntityAggroCache().getIfPresent(entity.getUniqueId());
        if (aggro != null) {
            String result = Util.createChainedCause(plugin, entity, aggro);
            if (debug) logger.info("[Debug] resolveRemover: Using aggro cache: " + result);
            return result;
        }

        String result = Util.createChainedCause(plugin, entity, null);
        if (debug) logger.info("[Debug] resolveRemover: Fallback to entity name: " + result);
        return result;
    }

    private void logFrameRemoval(ItemFrame itemFrame, String reason) {
        Material frameMaterial = itemFrame.isGlowing() ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME;
        plugin.getApi().logRemoval(reason, itemFrame.getLocation(), frameMaterial, null);
        if (itemFrame.getItem().getType() != Material.AIR) {
            plugin.getApi().logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
        }
    }
}