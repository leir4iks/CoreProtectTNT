package com.leir4iks.coreprotecttnt.listeners;

import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import com.leir4iks.coreprotecttnt.config.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class ExplosionListener implements Listener {
    private final Main plugin;
    private final Logger logger;

    private static final double INTERACTIVE_EXPLOSION_RADIUS = 5.0;
    private static final double INTERACTIVE_EXPLOSION_ITEM_VELOCITY_MULTIPLIER = 0.8;
    private static final double MACE_PLAYER_SEARCH_RADIUS = 2.0;
    private static final double WIND_CHARGE_MACE_SEARCH_RADIUS = 1.5;

    public ExplosionListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    private void handleWindExplosion(List<Block> blockList, String reason, Location center) {
        Iterator<Block> it = blockList.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            Material type = block.getType();
            BlockData data = block.getBlockData();
            boolean shouldRemoveFromList = true;
            boolean logInteraction = false;
            boolean logRemoval = false;

            if (type == Material.DECORATED_POT || type == Material.CHORUS_FLOWER) {
                shouldRemoveFromList = false;
                logRemoval = true;
            } else if (data instanceof Openable openable) {
                if (isWooden(type) || Tag.FENCE_GATES.isTagged(type)) {
                    if (data instanceof Door door) {
                        if (door.getHalf() == Bisected.Half.BOTTOM) {
                            toggleOpenable(block, openable);
                            logInteraction = true;
                        }
                    } else {
                        toggleOpenable(block, openable);
                        logInteraction = true;
                    }
                }
            } else if (type == Material.LEVER && data instanceof Powerable powerable) {
                powerable.setPowered(!powerable.isPowered());
                block.setBlockData(powerable, true);
                logInteraction = true;
            } else if (Tag.WOODEN_BUTTONS.isTagged(type) && data instanceof Powerable powerable) {
                if (!powerable.isPowered()) {
                    powerable.setPowered(true);
                    block.setBlockData(powerable, true);
                    logInteraction = true;

                    Main.getScheduler().runTaskLater(block.getLocation(), () -> {
                        if (block.getType() == type) {
                            BlockData currentData = block.getBlockData();
                            if (currentData instanceof Powerable p) {
                                p.setPowered(false);
                                block.setBlockData(p, true);
                            }
                        }
                    }, 30L);
                }
            } else if (type == Material.BELL) {
                logInteraction = true;
            } else if (data instanceof Lightable lightable && Tag.CANDLES.isTagged(type)) {
                if (lightable.isLit()) {
                    lightable.setLit(false);
                    block.setBlockData(lightable, true);
                    logInteraction = true;
                }
            }

            if (logInteraction) {
                this.plugin.getApi().logInteraction(reason, block.getLocation());
            }

            if (logRemoval) {
                this.plugin.getApi().logRemoval(reason, block.getLocation(), type, data);
            }

            if (shouldRemoveFromList) {
                it.remove();
            }
        }

        handleItemVelocity(center);
    }

    private void toggleOpenable(Block block, Openable openable) {
        openable.setOpen(!openable.isOpen());
        block.setBlockData(openable, true);
    }

    private boolean isWooden(Material type) {
        return Tag.WOODEN_DOORS.isTagged(type) || Tag.WOODEN_TRAPDOORS.isTagged(type);
    }

    private void handleItemVelocity(Location center) {
        BoundingBox searchBox = BoundingBox.of(center, INTERACTIVE_EXPLOSION_RADIUS, INTERACTIVE_EXPLOSION_RADIUS, INTERACTIVE_EXPLOSION_RADIUS);
        for (Entity entity : center.getWorld().getNearbyEntities(searchBox)) {
            if (entity instanceof Item item) {
                Main.getScheduler().runTaskLater(entity.getLocation(), () -> {
                    Vector direction = item.getLocation().toVector().subtract(center.toVector()).normalize();
                    item.setVelocity(item.getVelocity().add(direction.multiply(INTERACTIVE_EXPLOSION_ITEM_VELOCITY_MULTIPLIER)));
                }, 1L);
            }
        }
    }

    private boolean isHoldingMace(Player player) {
        PlayerInventory inventory = player.getInventory();
        return inventory.getItemInMainHand().getType() == Material.MACE || inventory.getItemInOffHand().getType() == Material.MACE;
    }

    private boolean isHoldingSpear(Player player) {
        PlayerInventory inventory = player.getInventory();
        return isSpear(inventory.getItemInMainHand()) || isSpear(inventory.getItemInOffHand());
    }

    private boolean isSpear(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type.name().endsWith("_SPEAR");
    }

    private void logExplosionOutcome(Location center, List<Block> blocks, float yield, String reason) {
        for (Block block : blocks) {
            this.plugin.getApi().logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
        }
        if (!blocks.isEmpty()) {
            handleHangingEntitiesInExplosion(center, yield, reason);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof Mob mob)) return;

        String cause = this.plugin.getEntityAggroCache().getIfPresent(mob.getUniqueId());
        if (cause == null && mob.getTarget() instanceof Player target) {
            cause = target.getName();
        }

        if (cause != null) {
            String reason = Util.createChainedCause(plugin, mob, cause);

            if (e.getTo() == Material.AIR) {
                this.plugin.getApi().logRemoval(reason, e.getBlock().getLocation(), e.getBlock().getType(), e.getBlock().getBlockData());
            } else {
                this.plugin.getApi().logPlacement(reason, e.getBlock().getLocation(), e.getTo(), e.getBlockData());
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent e) {
        Config config = plugin.getConfigManager().get();
        boolean isDebug = config.general.debug;

        if (isDebug) {
            logger.info("[Debug] Event: BlockExplodeEvent | Block: " + e.getBlock().getType() + " | Yield: " + e.getYield());
        }

        if (e.getBlock().getType() == Material.AIR) {
            Location explosionCenter = e.getBlock().getLocation();
            for (Player player : explosionCenter.getWorld().getNearbyPlayers(explosionCenter, MACE_PLAYER_SEARCH_RADIUS)) {
                String weapon = null;
                if (isHoldingMace(player)) {
                    weapon = "mace";
                } else if (isHoldingSpear(player)) {
                    weapon = "spear";
                }

                if (weapon != null) {
                    String reason = Util.createChainedCause(plugin, weapon, player.getName());
                    if (isDebug) logger.info("[Debug] Cause: " + weapon + " wind burst by " + player.getName());

                    handleWindExplosion(e.blockList(), reason, explosionCenter);
                    return;
                }
            }
        }

        if (e.getYield() == 0.0f) {
            return;
        }

        if (!config.modules.blockExplosions.enabled) return;

        Location location = e.getBlock().getLocation();
        String initiator = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(location));

        if (initiator == null && config.modules.blockExplosions.disableUnknown) {
            e.blockList().clear();
            Util.broadcastNearPlayers(location, config.localization.messages.unknownSourceAlert);
            return;
        }

        if (initiator != null) {
            if (isDebug) logger.info("[Debug] Logging block explosion removal caused by: " + initiator);
            this.plugin.addExplosion(location, 5.0, initiator);
            logExplosionOutcome(e.getBlock().getLocation(), e.blockList(), e.getYield(), initiator);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent e) {
        Config config = plugin.getConfigManager().get();
        boolean isDebug = config.general.debug;

        if (isDebug) {
            logger.info("[Debug] Event: EntityExplodeEvent | Entity: " + e.getEntityType().name() + " | Yield: " + e.getYield());
        }

        if (e.getEntity() instanceof AbstractWindCharge) {
            if (!config.modules.entityExplosions.enabled) return;

            String track = Util.getEntityExplosionCause(e.getEntity(), plugin);
            String shooterName = track;
            if (shooterName == null) shooterName = Util.getTranslatedName(plugin, "world");

            boolean isWeaponRelated = false;
            String weaponType = "mace";

            Player shooter = this.plugin.getServer().getPlayerExact(Util.getRootCause(plugin, shooterName));
            if (shooter != null && shooter.getWorld().equals(e.getLocation().getWorld()) &&
                    shooter.getLocation().distanceSquared(e.getLocation()) < WIND_CHARGE_MACE_SEARCH_RADIUS * WIND_CHARGE_MACE_SEARCH_RADIUS) {
                if (isHoldingMace(shooter)) {
                    isWeaponRelated = true;
                } else if (isHoldingSpear(shooter)) {
                    isWeaponRelated = true;
                    weaponType = "spear";
                }
            }

            boolean isTrackedWeapon = track != null && (track.contains("mace") || track.contains("spear"));
            String reason;

            if (isWeaponRelated || isTrackedWeapon) {
                if (isTrackedWeapon) {
                    reason = track;
                } else {
                    reason = Util.createChainedCause(plugin, weaponType, shooterName);
                }
            } else {
                reason = (track != null) ? track : Util.createChainedCause(plugin, "wind_charge", shooterName);
            }

            if (isDebug) logger.info("[Debug] WindCharge/Weapon explosion. Reason: " + reason);

            handleWindExplosion(e.blockList(), reason, e.getLocation());
            return;
        }

        if (e.getYield() == 0.0f || e.blockList().isEmpty()) {
            return;
        }

        if (!config.modules.entityExplosions.enabled) return;

        String track = Util.getEntityExplosionCause(e.getEntity(), plugin);
        if (track == null) {
            if (config.modules.entityExplosions.disableUnknown) {
                e.blockList().clear();
                Util.broadcastNearPlayers(e.getLocation(), config.localization.messages.unknownSourceAlert);
            }
            return;
        }

        String translatedName = Util.getTranslatedName(plugin, e.getEntityType().name().toLowerCase(Locale.ROOT));
        String fullPrefix = config.formatting.logPrefix + translatedName;
        String reason;

        if (track.startsWith(fullPrefix)) {
            reason = track;
        } else {
            reason = Util.createChainedCause(plugin, e.getEntity(), track);
        }

        if (isDebug) {
            logger.info("[Debug] Logging entity explosion removal caused by: " + reason);
        }

        this.plugin.addExplosion(e.getLocation(), Util.getExplosionRadius(e.getEntityType(), e.getEntity()) + 2.0, reason);
        logExplosionOutcome(e.getLocation(), e.blockList(), e.getYield(), reason);
        this.plugin.getProjectileCache().invalidate(e.getEntity().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getHitBlock() == null) return;
        if (!(e.getEntity() instanceof AbstractWindCharge)) return;

        if (plugin.getConfigManager().get().general.debug) {
            String shooterName = this.plugin.getProjectileCache().getIfPresent(e.getEntity().getUniqueId());
            logger.info("[Debug] WindCharge hit block: " + e.getHitBlock().getType() + " by " + shooterName);
        }

        Material type = e.getHitBlock().getType();
        if (type == Material.DECORATED_POT || type == Material.CHORUS_FLOWER) {
            String shooterName = this.plugin.getProjectileCache().getIfPresent(e.getEntity().getUniqueId());
            if (shooterName == null) shooterName = Util.createChainedCause(plugin, "wind_charge", null);
            this.plugin.getApi().logRemoval(shooterName, e.getHitBlock().getLocation(), type, e.getHitBlock().getBlockData());
        }
    }

    private void handleHangingEntitiesInExplosion(Location center, float yield, String reason) {
        double radius = Math.max(yield, 5.0f);
        Collection<Hanging> hangingEntities = center.getWorld().getNearbyEntities(center, radius, radius, radius, entity -> entity instanceof Hanging).stream()
                .map(Hanging.class::cast)
                .toList();

        for (Hanging hanging : hangingEntities) {
            if (hanging.isDead()) continue;
            if (plugin.getProcessedEntities().getIfPresent(hanging.getUniqueId()) != null) continue;

            plugin.getProcessedEntities().put(hanging.getUniqueId(), true);

            Main.getScheduler().runTaskLater(hanging.getLocation(), () -> {
                removeHanging(hanging, reason);
                hanging.remove();
            }, 1L);
        }
    }

    private void removeHanging(Hanging hanging, String reason) {
        if (hanging instanceof ItemFrame itemFrame) {
            Material frameMaterial = itemFrame.isGlowing() ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME;
            plugin.getApi().logRemoval(reason, itemFrame.getLocation(), frameMaterial, null);
            if (itemFrame.getItem().getType() != Material.AIR) {
                plugin.getApi().logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
            }
        } else {
            plugin.getApi().logRemoval(reason, hanging.getLocation(), Material.PAINTING, null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractBedOrRespawnAnchorExplosion(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null) return;

        if (clickedBlock.getBlockData() instanceof Bed) {
            this.plugin.getBlockPlaceCache().put(Main.BlockKey.from(clickedBlock.getLocation()), Util.createChainedCause(plugin, "bed", e.getPlayer().getName()));
        } else if (clickedBlock.getBlockData() instanceof RespawnAnchor) {
            this.plugin.getBlockPlaceCache().put(Main.BlockKey.from(clickedBlock.getLocation()), Util.createChainedCause(plugin, "respawn_anchor", e.getPlayer().getName()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractCreeper(PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getType() == EntityType.CREEPER && e.getPlayer().getInventory().getItemInMainHand().getType() == Material.FLINT_AND_STEEL) {
            this.plugin.getEntityAggroCache().put(e.getRightClicked().getUniqueId(), Util.createChainedCause(plugin, "ignite_creeper", e.getPlayer().getName()));
        }
    }
}