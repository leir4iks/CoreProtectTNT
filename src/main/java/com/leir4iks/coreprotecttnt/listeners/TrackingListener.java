package com.leir4iks.coreprotecttnt.listeners;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.leir4iks.coreprotecttnt.Main;
import com.leir4iks.coreprotecttnt.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TrackingListener implements Listener {
    private final Main plugin;
    private final Logger logger;
    private static final BlockFace[] SEARCH_FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private final Cache<Main.BlockKey, String> tntCalculationCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
            .maximumSize(5000)
            .build();

    public TrackingListener(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getConfigManager().get().general.debug) {
            logger.info("[Debug] Caching block place at " + event.getBlock().getLocation() + " by " + event.getPlayer().getName());
        }
        this.plugin.getBlockPlaceCache().put(Main.BlockKey.from(event.getBlock().getLocation()), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getConfigManager().get().general.debug) {
            logger.info("[Debug] Caching block break at " + event.getBlock().getLocation() + " by " + event.getPlayer().getName());
        }
        this.plugin.getBlockPlaceCache().put(Main.BlockKey.from(event.getBlock().getLocation()), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (e.getPlayer() == null) return;
        EntityType type = e.getEntityType();
        if (type == EntityType.TNT_MINECART || type == EntityType.END_CRYSTAL) {
            if (plugin.getConfigManager().get().general.debug) {
                logger.info("[Debug] Caching entity place " + type + " by " + e.getPlayer().getName());
            }
            this.plugin.getProjectileCache().put(e.getEntity().getUniqueId(), e.getPlayer().getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamageEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player && e.getEntity() != null) {
            this.plugin.getEntityAggroCache().put(e.getEntity().getUniqueId(), e.getDamager().getName());
        } else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            this.plugin.getEntityAggroCache().put(e.getEntity().getUniqueId(), player.getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player player) {
            this.plugin.getEntityAggroCache().put(e.getEntity().getUniqueId(), player.getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() == EntityType.WITHER && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
            Location spawnLoc = e.getLocation();
            String placer = null;

            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 3; y++) {
                    for (int z = -2; z <= 2; z++) {
                        Block block = spawnLoc.getBlock().getRelative(x, y, z);
                        if (block.getType() == Material.SOUL_SAND || block.getType() == Material.SOUL_SOIL) {
                            placer = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(block.getLocation()));
                            if (placer != null) break;
                        }
                    }
                    if (placer != null) break;
                }
                if (placer != null) break;
            }

            if (placer != null) {
                this.plugin.getEntityAggroCache().put(e.getEntity().getUniqueId(), placer);
            }
        } else if (e.getEntityType() == EntityType.ENDER_DRAGON) {
            String respawner = this.plugin.getDragonRespawners().remove(e.getLocation().getWorld().getUID());
            if (respawner != null) {
                this.plugin.getEntityAggroCache().put(e.getEntity().getUniqueId(), respawner);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCrystalPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null || e.getItem().getType() != Material.END_CRYSTAL) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.BEDROCK) return;
        if (e.getPlayer().getWorld().getEnvironment() != World.Environment.THE_END) return;

        this.plugin.getDragonRespawners().put(e.getPlayer().getWorld().getUID(), e.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockDispense(BlockDispenseEvent e) {
        if (e.getItem().getType() != Material.TNT) return;

        Block block = e.getBlock();
        String placer = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(block.getLocation()));

        String cause = (placer != null)
                ? Util.createChainedCause(plugin, "tnt", placer)
                : Util.createChainedCause(plugin, "tnt", "@[" + block.getX() + "," + block.getY() + "," + block.getZ() + "]");

        this.plugin.getDispenserCache().put(Main.BlockKey.from(block.getLocation()), cause);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Block piston = e.getBlock();
        String placer = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(piston.getLocation()));
        if (placer == null) return;

        if (piston.getBlockData() instanceof Directional directional) {
            BlockFace face = directional.getFacing();

            Block extensionBlock = piston.getRelative(face);
            this.plugin.getBlockPlaceCache().put(Main.BlockKey.from(extensionBlock.getLocation()), placer);

            List<Block> pushedBlocks = e.getBlocks();
            for (Block b : pushedBlocks) {
                Block targetBlock = b.getRelative(face);
                this.plugin.getBlockPlaceCache().put(Main.BlockKey.from(targetBlock.getLocation()), placer);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Block piston = e.getBlock();
        String placer = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(piston.getLocation()));
        if (placer == null) return;

        if (piston.getBlockData() instanceof Directional directional) {
            BlockFace face = directional.getFacing();

            List<Block> pulledBlocks = e.getBlocks();
            for (Block b : pulledBlocks) {
                Block targetBlock = b.getRelative(face.getOppositeFace());
                this.plugin.getBlockPlaceCache().put(Main.BlockKey.from(targetBlock.getLocation()), placer);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        Projectile projectile = e.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (shooter == null) return;

        String finalCause = Util.getTranslatedName(plugin, "world");
        String projectileName = projectile.getType().name().toLowerCase(Locale.ROOT);

        if (shooter instanceof Player player) {
            if (projectile instanceof AbstractWindCharge) {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand.getType() == Material.MACE && mainHand.getEnchantmentLevel(Enchantment.WIND_BURST) > 0) {
                    projectileName = "mace";
                } else if (mainHand.getType().name().endsWith("_SPEAR") && mainHand.getEnchantmentLevel(Enchantment.WIND_BURST) > 0) {
                    projectileName = "spear";
                }
            } else if (projectile instanceof Trident) {
                projectileName = "trident";
            }

            finalCause = Util.createChainedCause(plugin, projectileName, player.getName());
            this.plugin.getPlayerProjectileCache().put(player.getUniqueId(), finalCause);
        } else if (shooter instanceof Mob mob) {
            String trackedAggressor = this.plugin.getEntityAggroCache().getIfPresent(mob.getUniqueId());

            if (trackedAggressor == null && mob.getTarget() instanceof Player target) {
                trackedAggressor = target.getName();
                this.plugin.getEntityAggroCache().put(mob.getUniqueId(), trackedAggressor);
            }

            if (trackedAggressor != null) {
                finalCause = Util.createChainedCause(plugin, projectileName, Util.createChainedCause(plugin, mob, trackedAggressor));
            } else {
                finalCause = Util.createChainedCause(plugin, projectileName, Util.createChainedCause(plugin, mob, null));
            }
        } else if (shooter instanceof BlockProjectileSource bps) {
            Location loc = bps.getBlock().getLocation();
            String blockInitiator = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(loc));
            if (blockInitiator != null) {
                finalCause = Util.createChainedCause(plugin, projectileName, blockInitiator);
            } else {
                String dispenser = Util.getTranslatedName(plugin, "dispenser");
                finalCause = Util.createChainedCause(plugin, projectileName, dispenser + "@[" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "]");
            }
        }

        if (plugin.getConfigManager().get().general.debug) {
            logger.info("[Debug] Caching projectile " + projectile.getUniqueId() + " with cause: " + finalCause);
        }
        this.plugin.getProjectileCache().put(projectile.getUniqueId(), finalCause);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIgniteTNT(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed tntPrimed)) return;

        Location tntLoc = tntPrimed.getLocation();
        Location blockLoc = tntLoc.getBlock().getLocation();
        Main.BlockKey cacheKey = Main.BlockKey.from(blockLoc);

        String cachedInitiator = tntCalculationCache.getIfPresent(cacheKey);
        if (cachedInitiator != null) {
            this.plugin.getProjectileCache().put(tntPrimed.getUniqueId(), cachedInitiator);
            return;
        }

        String initiator = this.plugin.getExplosionSource(tntLoc);

        if (initiator == null) {
            Entity source = tntPrimed.getSource();
            if (source != null) {
                initiator = this.plugin.getProjectileCache().getIfPresent(source.getUniqueId());
                if (initiator == null) {
                    if (source instanceof Player) {
                        initiator = source.getName();
                    } else {
                        String aggro = this.plugin.getEntityAggroCache().getIfPresent(source.getUniqueId());
                        if (aggro == null && source instanceof Mob mob && mob.getTarget() instanceof Player target) {
                            aggro = target.getName();
                        }

                        if (aggro != null) {
                            initiator = Util.createChainedCause(plugin, source, aggro);
                        } else {
                            initiator = Util.createChainedCause(plugin, source, null);
                        }
                    }
                }
            }
        }

        if (initiator == null) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Main.BlockKey key = Main.BlockKey.from(tntLoc.getWorld().getUID(), blockLoc.getBlockX() + x, blockLoc.getBlockY() + y, blockLoc.getBlockZ() + z);
                        initiator = this.plugin.getDispenserCache().getIfPresent(key);
                        if (initiator != null) break;
                    }
                    if (initiator != null) break;
                }
                if (initiator != null) break;
            }
        }

        if (initiator == null) {
            initiator = findRedstoneActivator(blockLoc);
        }

        if (initiator == null) {
            initiator = this.plugin.getBlockPlaceCache().getIfPresent(cacheKey);
        }

        if (initiator == null) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block block = tntLoc.getBlock().getRelative(x, y, z);
                        if (block.getType() == Material.DISPENSER || block.getType() == Material.DROPPER) {
                            String dispenserPlacer = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(block.getLocation()));
                            if (dispenserPlacer != null) {
                                initiator = Util.createChainedCause(plugin, "tnt", dispenserPlacer);
                                break;
                            }
                        }
                    }
                    if (initiator != null) break;
                }
                if (initiator != null) break;
            }
        }

        if (initiator != null) {
            if (plugin.getConfigManager().get().general.debug) logger.info("[Debug] Tracking TNT " + tntPrimed.getUniqueId() + " from source: " + initiator);
            this.plugin.getProjectileCache().put(tntPrimed.getUniqueId(), initiator);
            tntCalculationCache.put(cacheKey, initiator);
        }
    }

    private String findRedstoneActivator(Location tntBlockLoc) {
        for (BlockFace face : SEARCH_FACES) {
            Block relative = tntBlockLoc.getBlock().getRelative(face);
            Material type = relative.getType();

            if (type == Material.AIR) continue;

            if (type == Material.REDSTONE_WIRE ||
                    type == Material.REDSTONE_TORCH ||
                    type == Material.REDSTONE_WALL_TORCH ||
                    type == Material.LEVER ||
                    type == Material.REPEATER ||
                    type == Material.COMPARATOR ||
                    type == Material.OBSERVER ||
                    type == Material.DAYLIGHT_DETECTOR ||
                    type == Material.SCULK_SENSOR ||
                    type == Material.CALIBRATED_SCULK_SENSOR ||
                    type == Material.TARGET ||
                    type == Material.LIGHTNING_ROD ||
                    Tag.BUTTONS.isTagged(type) ||
                    Tag.PRESSURE_PLATES.isTagged(type)) {

                String placer = this.plugin.getBlockPlaceCache().getIfPresent(Main.BlockKey.from(relative.getLocation()));
                if (placer != null) {
                    return Util.createChainedCause(plugin, "tnt", placer);
                }
            }
        }
        return null;
    }
}