package com.leir4iks.coreprotecttnt;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Main extends JavaPlugin implements Listener {
   private final Cache<Object, String> probablyCache;
   private CoreProtectAPI api;

   public Main() {
      this.probablyCache = CacheBuilder.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).concurrencyLevel(4).maximumSize(50000L).build();
   }

   @Override
   public void onEnable() {
      saveDefaultConfig();
      Plugin depend = Bukkit.getPluginManager().getPlugin("CoreProtect");
      if (depend instanceof CoreProtect) {
         this.api = ((CoreProtect) depend).getAPI();
         Bukkit.getPluginManager().registerEvents(this, this);
      } else {
         getLogger().severe("CoreProtect not found or invalid version. Disabling plugin.");
         this.getPluginLoader().disablePlugin(this);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onPlayerInteractBedOrRespawnAnchorExplosion(PlayerInteractEvent e) {
      if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

      Block clickedBlock = e.getClickedBlock();
      if (clickedBlock == null) return;

      BlockData blockData = clickedBlock.getBlockData();
      if (blockData instanceof Bed) {
         Bed bed = (Bed) blockData;
         Location headLocation = (bed.getPart() == Bed.Part.HEAD) ? clickedBlock.getLocation() : clickedBlock.getLocation().add(bed.getFacing().getDirection());
         Location footLocation = (bed.getPart() == Bed.Part.FOOT) ? clickedBlock.getLocation() : clickedBlock.getLocation().subtract(bed.getFacing().getDirection());
         String reason = "#bed-" + e.getPlayer().getName();
         this.probablyCache.put(headLocation.toBlockLocation(), reason);
         this.probablyCache.put(footLocation.toBlockLocation(), reason);
      } else if (blockData instanceof RespawnAnchor) {
         this.probablyCache.put(clickedBlock.getLocation(), "#respawnanchor-" + e.getPlayer().getName());
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onPlayerInteractCreeper(PlayerInteractEntityEvent e) {
      if (e.getRightClicked() instanceof Creeper && e.getPlayer().getInventory().getItemInMainHand().getType() == Material.FLINT_AND_STEEL) {
         this.probablyCache.put(e.getRightClicked(), "#ignitecreeper-" + e.getPlayer().getName());
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onBlockExplode(BlockExplodeEvent e) {
      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "block-explosion");
      if (!section.getBoolean("enable", true)) return;

      Location location = e.getBlock().getLocation();
      String probablyCauses = this.probablyCache.getIfPresent(location);

      if (probablyCauses == null) {
         probablyCauses = this.probablyCache.getIfPresent(location.toBlockLocation());
      }

      if (probablyCauses == null) {
         if (section.getBoolean("disable-unknown", true)) {
            e.blockList().clear();
            Util.broadcastNearPlayers(location, section.getString("alert"));
         }
         return;
      }

      for (Block block : e.blockList()) {
         this.api.logRemoval(probablyCauses, block.getLocation(), block.getType(), block.getBlockData());
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onBlockPlace(BlockPlaceEvent event) {
      this.probablyCache.put(event.getBlock().getLocation(), event.getPlayer().getName());
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onBlockBreak(BlockBreakEvent event) {
      this.probablyCache.put(event.getBlock().getLocation(), event.getPlayer().getName());
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onClickItemFrame(PlayerInteractEntityEvent e) {
      if (!(e.getRightClicked() instanceof ItemFrame)) return;

      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "itemframe");
      if (!section.getBoolean("enable", true)) return;

      ItemFrame itemFrame = (ItemFrame) e.getRightClicked();
      Player player = e.getPlayer();
      String playerName = player.getName();

      this.api.logInteraction(playerName, itemFrame.getLocation());

      boolean hasItem = itemFrame.getItem().getType() != Material.AIR;
      ItemStack mainHandItem = player.getInventory().getItemInMainHand();
      ItemStack offHandItem = player.getInventory().getItemInOffHand();
      boolean placingItem = !mainHandItem.getType().isAir() || !offHandItem.getType().isAir();

      if (!hasItem && placingItem) {
         ItemStack putIn = !mainHandItem.getType().isAir() ? mainHandItem : offHandItem;
         this.api.logPlacement("#additem-" + playerName, itemFrame.getLocation(), putIn.getType(), null);
      } else if (hasItem) {
         this.api.logRemoval("#rotate-" + playerName, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
         this.api.logPlacement("#rotate-" + playerName, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onProjectileLaunch(ProjectileLaunchEvent e) {
      Projectile projectile = e.getEntity();
      if (projectile.getShooter() == null) return;

      String shooterName = "world";
      if (projectile.getShooter() instanceof Player) {
         shooterName = ((Player) projectile.getShooter()).getName();
      } else if (projectile.getShooter() instanceof Entity) {
         shooterName = ((Entity) projectile.getShooter()).getName();
      }

      this.probablyCache.put(projectile, shooterName);
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onIgniteTNT(EntitySpawnEvent e) {
      if (!(e.getEntity() instanceof TNTPrimed)) return;

      TNTPrimed tntPrimed = (TNTPrimed) e.getEntity();
      Entity source = tntPrimed.getSource();

      if (source != null) {
         String sourceFromCache = this.probablyCache.getIfPresent(source);
         if (sourceFromCache != null) {
            this.probablyCache.put(tntPrimed, sourceFromCache);
            return;
         }
         if (source instanceof Player) {
            this.probablyCache.put(tntPrimed, source.getName());
            return;
         }
      }

      Location blockLocation = tntPrimed.getLocation().getBlock().getLocation();
      String reason = this.probablyCache.getIfPresent(blockLocation);
      if (reason != null) {
         this.probablyCache.put(tntPrimed, reason);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onHangingBreak(HangingBreakEvent e) {
      if (e.getCause() == HangingBreakEvent.RemoveCause.ENTITY) return;

      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "hanging");
      if (!section.getBoolean("enable", true)) return;

      Location hangingLocation = e.getEntity().getLocation().getBlock().getLocation();
      String reason = this.probablyCache.getIfPresent(hangingLocation);
      if (reason == null) {
         if (section.getBoolean("disable-unknown")) {
            e.setCancelled(true);
            Util.broadcastNearPlayers(e.getEntity().getLocation(), section.getString("alert"));
         }
         return;
      }

      String materialName = e.getEntity().getType().name();
      Material material = Material.matchMaterial(materialName);
      String cause = "#" + e.getCause().name() + "-" + reason;

      if (material != null) {
         this.api.logRemoval(cause, hangingLocation, material, null);
      } else {
         this.api.logInteraction(cause, hangingLocation);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onEndCrystalHit(EntityDamageByEntityEvent e) {
      if (!(e.getEntity() instanceof EnderCrystal)) return;

      String damagerName = null;
      if (e.getDamager() instanceof Player) {
         damagerName = e.getDamager().getName();
      } else {
         damagerName = this.probablyCache.getIfPresent(e.getDamager());
         if (damagerName == null && e.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) e.getDamager()).getShooter();
            if (shooter instanceof Player) {
               damagerName = ((Player) shooter).getName();
            }
         }
      }

      if (damagerName != null) {
         this.probablyCache.put(e.getEntity(), damagerName);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onHangingHit(HangingBreakByEntityEvent e) {
       if (!(e.getEntity() instanceof ItemFrame) && !(e.getEntity() instanceof Painting)) return;

       ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), e.getEntity() instanceof ItemFrame ? "itemframe" : "hanging");
       if (!section.getBoolean("enable", true)) return;

       Hanging hanging = e.getEntity();
       String removerName = null;

       if (e.getRemover() instanceof Player) {
           removerName = e.getRemover().getName();
       } else if (e.getRemover() != null) {
           removerName = probablyCache.getIfPresent(e.getRemover());
           if (removerName == null) {
               removerName = "#" + e.getRemover().getType().name().toLowerCase(Locale.ROOT);
           } else {
               removerName = "#" + e.getRemover().getType().name().toLowerCase(Locale.ROOT) + "-" + removerName;
           }
       }

       if (removerName == null) {
           if (section.getBoolean("disable-unknown")) {
               e.setCancelled(true);
               Util.broadcastNearPlayers(hanging.getLocation(), section.getString("alert"));
           }
           return;
       }
       
       Material material = hanging.getType() == EntityType.ITEM_FRAME ? Material.ITEM_FRAME : Material.PAINTING;
       api.logRemoval(removerName, hanging.getLocation(), material, null);

       if (hanging instanceof ItemFrame) {
           ItemFrame itemFrame = (ItemFrame) hanging;
           if (itemFrame.getItem() != null && itemFrame.getItem().getType() != Material.AIR) {
               api.logRemoval(removerName, hanging.getLocation(), itemFrame.getItem().getType(), null);
           }
       }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onEntityHitByProjectile(EntityDamageByEntityEvent e) {
      if (!(e.getDamager() instanceof Projectile)) return;

      Projectile projectile = (Projectile) e.getDamager();
      ProjectileSource shooter = projectile.getShooter();
      if (shooter == null) return;

      String sourceName = this.probablyCache.getIfPresent(projectile);
      if (sourceName == null) {
         if (shooter instanceof Player) {
            sourceName = ((Player) shooter).getName();
         } else if (shooter instanceof Entity) {
            sourceName = ((Entity) shooter).getName();
         }
      }

      if (sourceName != null) {
         this.probablyCache.put(e.getEntity(), sourceName);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onBlockIgnite(BlockIgniteEvent e) {
      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "fire");
      if (!section.getBoolean("enable", true)) return;

      String sourceName = null;
      if (e.getPlayer() != null) {
         sourceName = e.getPlayer().getName();
      } else if (e.getIgnitingEntity() != null) {
         sourceName = this.probablyCache.getIfPresent(e.getIgnitingEntity());
         if (sourceName == null) {
             sourceName = "#" + e.getIgnitingEntity().getType().name().toLowerCase(Locale.ROOT);
         }
      } else if (e.getIgnitingBlock() != null) {
         sourceName = this.probablyCache.getIfPresent(e.getIgnitingBlock().getLocation());
      }
      
      if (sourceName != null) {
         this.probablyCache.put(e.getBlock().getLocation(), sourceName);
      } else if (section.getBoolean("disable-unknown", true)) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
   public void onBlockBurn(BlockBurnEvent e) {
      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "fire");
      if (!section.getBoolean("enable", true)) return;

      if (e.getIgnitingBlock() == null) return;
      
      String sourceFromCache = this.probablyCache.getIfPresent(e.getIgnitingBlock().getLocation());
      if (sourceFromCache != null) {
         this.probablyCache.put(e.getBlock().getLocation(), sourceFromCache);
         this.api.logRemoval("#fire-" + sourceFromCache, e.getBlock().getLocation(), e.getBlock().getType(), e.getBlock().getBlockData());
      } else if (section.getBoolean("disable-unknown", true)) {
         e.setCancelled(true);
         Util.broadcastNearPlayers(e.getIgnitingBlock().getLocation(), section.getString("alert"));
      }
   }

   @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
   public void onBombHit(ProjectileHitEvent e) {
      if (e.getHitEntity() == null) return;
      if (!(e.getHitEntity() instanceof ExplosiveMinecart) && e.getHitEntity().getType() != EntityType.ENDER_CRYSTAL) return;

      String source = this.probablyCache.getIfPresent(e.getEntity());
      if (source == null && e.getEntity().getShooter() instanceof Player) {
         source = ((Player) e.getEntity().getShooter()).getName();
      }

      if (source != null) {
         this.probablyCache.put(e.getHitEntity(), source);
      }
   }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent e) {
        EntityType entityType = e.getEntityType();

        if (entityType == EntityType.WIND_CHARGE || entityType.name().equals("WIND_BURST") || entityType.name().equals("BREEZE_WIND_CHARGE")) {
            e.blockList().clear();
            return;
        }

        if (e.blockList().isEmpty()) return;

        ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "entity-explosion");
        if (!section.getBoolean("enable", true)) return;

        Entity entity = e.getEntity();
        String track = this.probablyCache.getIfPresent(entity);

        if (track == null) {
            if (entity instanceof Creeper) {
                LivingEntity target = ((Creeper) entity).getTarget();
                if (target != null) {
                    track = "#creeper-" + target.getName();
                }
            } else if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) entity.getLastDamageCause()).getDamager();
                track = this.probablyCache.getIfPresent(damager);
                if (track == null) {
                    track = "#" + damager.getType().name().toLowerCase(Locale.ROOT);
                }
            }
        }

        if (track == null) {
            if (section.getBoolean("disable-unknown")) {
                e.blockList().clear();
                Util.broadcastNearPlayers(e.getLocation(), section.getString("alert"));
            }
            return;
        }

        String reason = "#" + entityType.name().toLowerCase(Locale.ROOT) + "-" + track;
        for (Block block : e.blockList()) {
            this.api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
            this.probablyCache.put(block.getLocation(), reason);
        }
        this.probablyCache.invalidate(entity);
    }
}
