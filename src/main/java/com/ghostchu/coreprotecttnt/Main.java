package com.ghostchu.coreprotecttnt;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

public class Main extends JavaPlugin implements Listener {
   private final Cache<Object, String> probablyCache;
   private CoreProtectAPI api;

   public Main() {
      this.probablyCache = CacheBuilder.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).concurrencyLevel(4).maximumSize(50000L).recordStats().build();
   }

   public void onEnable() {
      Bukkit.getPluginManager().registerEvents(this, this);
      this.saveDefaultConfig();
      Plugin depend = Bukkit.getPluginManager().getPlugin("CoreProtect");
      if (depend == null) {
         this.getPluginLoader().disablePlugin(this);
      } else {
         this.api = ((CoreProtect)depend).getAPI();
      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onPlayerInteractBedOrRespawnAnchorExplosion(PlayerInteractEvent e) {
      if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Block clickedBlock = e.getClickedBlock();
         Location locationHead = clickedBlock.getLocation();
         BlockData var5;
         if ((var5 = clickedBlock.getBlockData()) instanceof Bed) {
            Bed bed = (Bed)var5;
            Location locationFoot = locationHead.clone().subtract(bed.getFacing().getDirection());
            if (bed.getPart() == Part.FOOT) {
               locationHead.add(bed.getFacing().getDirection());
            }

            String reason = "#bed-" + e.getPlayer().getName();
            this.probablyCache.put(locationHead, reason);
            this.probablyCache.put(locationFoot, reason);
         }

         if (clickedBlock.getBlockData() instanceof RespawnAnchor) {
            this.probablyCache.put(clickedBlock.getLocation(), "#respawnanchor-" + e.getPlayer().getName());
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onPlayerInteractCreeper(PlayerInteractEntityEvent e) {
      if (e.getRightClicked() instanceof Creeper) {
         this.probablyCache.put(e.getRightClicked(), "#ignitecreeper-" + e.getPlayer().getName());
      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onBlockExplode(BlockExplodeEvent e) {
      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "block-explosion");
      if (section.getBoolean("enable", true)) {
         Location location = e.getBlock().getLocation();
         String probablyCauses = (String)this.probablyCache.getIfPresent(e.getBlock());
         if (probablyCauses == null) {
            probablyCauses = (String)this.probablyCache.getIfPresent(location);
         }

         if (probablyCauses == null && section.getBoolean("disable-unknown", true)) {
            e.blockList().clear();
            Util.broadcastNearPlayers(location, section.getString("alert"));
         }

         Iterator var6 = e.blockList().iterator();

         while(var6.hasNext()) {
            Block block = (Block)var6.next();
            this.api.logRemoval(probablyCauses, block.getLocation(), block.getType(), block.getBlockData());
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onBlockPlaceOnHanging(BlockPlaceEvent event) {
      this.probablyCache.put(event.getBlock().getLocation(), event.getPlayer().getName());
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onBlockBreak(BlockPlaceEvent event) {
      this.probablyCache.put(event.getBlock().getLocation(), event.getPlayer().getName());
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onClickItemFrame(PlayerInteractEntityEvent e) {
      Entity var3;
      if ((var3 = e.getRightClicked()) instanceof ItemFrame) {
         ItemFrame itemFrame = (ItemFrame)var3;
         ConfigurationSection var4 = Util.bakeConfigSection(this.getConfig(), "itemframe");
         if (var4.getBoolean("enable", true)) {
            this.api.logInteraction(e.getPlayer().getName(), e.getRightClicked().getLocation());
            if (itemFrame.getItem().getType().isAir()) {
               ItemStack mainItem = e.getPlayer().getInventory().getItemInMainHand();
               ItemStack offItem = e.getPlayer().getInventory().getItemInOffHand();
               ItemStack putIn = mainItem.getType().isAir() ? offItem : mainItem;
               if (!putIn.getType().isAir()) {
                  this.api.logPlacement("#additem-" + e.getPlayer().getName(), e.getRightClicked().getLocation(), putIn.getType(), (BlockData)null);
                  return;
               }
            }

            this.api.logRemoval("#rotate-" + e.getPlayer().getName(), e.getRightClicked().getLocation(), itemFrame.getItem().getType(), (BlockData)null);
            this.api.logPlacement("#rotate-" + e.getPlayer().getName(), e.getRightClicked().getLocation(), itemFrame.getItem().getType(), (BlockData)null);
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onProjectileLaunch(ProjectileLaunchEvent e) {
      if (e.getEntity().getShooter() != null) {
         String entityType = e.getEntity().getType().name().toLowerCase(Locale.ROOT);
         if (!entityType.contains("wind_charge") && !entityType.contains("breeze_wind_charge")) {
            ProjectileSource projectileSource = e.getEntity().getShooter();
            String source = "";
            if (!(projectileSource instanceof Player)) {
               source = source + "#";
            }

            source = source + e.getEntity().getName() + "-";
            if (projectileSource instanceof Entity) {
               label26: {
                  Entity entity = (Entity)projectileSource;
                  if (projectileSource instanceof Mob) {
                     Mob mob = (Mob)projectileSource;
                     if (((Mob)projectileSource).getTarget() != null) {
                        source = source + mob.getTarget().getName();
                        break label26;
                     }
                  }

                  source = source + entity.getName();
               }
            } else if (projectileSource instanceof Block) {
               Block block = (Block)projectileSource;
               source = source + block.getType().name();
            } else {
               source = source + projectileSource.getClass().getName();
            }

            this.probablyCache.put(e.getEntity(), source);
            this.probablyCache.put(projectileSource, source);
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onIgniteTNT(EntitySpawnEvent e) {
      Entity tnt = e.getEntity();
      Entity var4;
      if ((var4 = e.getEntity()) instanceof TNTPrimed) {
         TNTPrimed tntPrimed = (TNTPrimed)var4;
         Entity source = tntPrimed.getSource();
         if (source != null) {
            String sourceFromCache = (String)this.probablyCache.getIfPresent(source);
            if (sourceFromCache != null) {
               this.probablyCache.put(tnt, sourceFromCache);
            }

            if (source.getType() == EntityType.PLAYER) {
               this.probablyCache.put(tntPrimed, source.getName());
               return;
            }
         }

         Location blockCorner = tnt.getLocation().clone().subtract(0.5D, 0.0D, 0.5D);
         Iterator var8 = this.probablyCache.asMap().entrySet().iterator();

         while(var8.hasNext()) {
            Entry<Object, String> entry = (Entry)var8.next();
            Object var10;
            if ((var10 = entry.getKey()) instanceof Location) {
               Location loc = (Location)var10;
               if (loc.getWorld().equals(blockCorner.getWorld()) && loc.distance(blockCorner) < 0.5D) {
                  this.probablyCache.put(tnt, (String)entry.getValue());
                  break;
               }
            }
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onHangingBreak(HangingBreakEvent e) {
      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "hanging");
      if (section.getBoolean("enable", true)) {
         if (e.getCause() != RemoveCause.PHYSICS && e.getCause() != RemoveCause.DEFAULT) {
            Block hangingPosBlock = e.getEntity().getLocation().getBlock();
            String reason = (String)this.probablyCache.getIfPresent(hangingPosBlock.getLocation());
            if (reason != null) {
               Material mat = Material.matchMaterial(e.getEntity().getType().name());
               if (mat != null) {
                  this.api.logRemoval("#" + e.getCause().name() + "-" + reason, hangingPosBlock.getLocation(), Material.matchMaterial(e.getEntity().getType().name()), (BlockData)null);
               } else {
                  this.api.logInteraction("#" + e.getCause().name() + "-" + reason, hangingPosBlock.getLocation());
               }
            }

         }
      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.LOWEST
   )
   public void onEndCrystalHit(EntityDamageByEntityEvent e) {
      if (e.getEntity() instanceof EnderCrystal) {
         if (e.getDamager() instanceof Player) {
            this.probablyCache.put(e.getEntity(), e.getDamager().getName());
         } else {
            String sourceFromCache = (String)this.probablyCache.getIfPresent(e.getDamager());
            if (sourceFromCache != null) {
               this.probablyCache.put(e.getEntity(), sourceFromCache);
            } else {
               Entity var4;
               if ((var4 = e.getDamager()) instanceof Projectile) {
                  Projectile projectile = (Projectile)var4;
                  ProjectileSource var6;
                  if (projectile.getShooter() != null && (var6 = projectile.getShooter()) instanceof Player) {
                     Player player = (Player)var6;
                     this.probablyCache.put(e.getEntity(), player.getName());
                  }
               }
            }
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onHangingHit(EntityDamageByEntityEvent e) {
      if (e.getEntity() instanceof Hanging) {
         ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "itemframe");
         if (section.getBoolean("enable", true)) {
            ItemFrame itemFrame = (ItemFrame)e.getEntity();
            if (!itemFrame.getItem().getType().isAir() && !itemFrame.isInvulnerable()) {
               if (e.getDamager() instanceof Player) {
                  this.probablyCache.put(e.getEntity(), e.getDamager().getName());
                  this.api.logInteraction(e.getDamager().getName(), itemFrame.getLocation());
                  this.api.logRemoval(e.getDamager().getName(), itemFrame.getLocation(), itemFrame.getItem().getType(), (BlockData)null);
               } else {
                  String cause = (String)this.probablyCache.getIfPresent(e.getDamager());
                  if (cause != null) {
                     String var10000 = e.getDamager().getName();
                     String reason = "#" + var10000 + "-" + cause;
                     this.probablyCache.put(e.getEntity(), reason);
                     this.api.logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), (BlockData)null);
                  }
               }

            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onPaintingHit(EntityDamageByEntityEvent e) {
      if (e.getEntity() instanceof Painting) {
         ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "painting");
         if (section.getBoolean("enable", true)) {
            ItemFrame itemFrame = (ItemFrame)e.getEntity();
            if (!itemFrame.getItem().getType().isAir() && !itemFrame.isInvulnerable()) {
               if (e.getDamager() instanceof Player) {
                  this.api.logInteraction(e.getDamager().getName(), itemFrame.getLocation());
                  this.api.logRemoval(e.getDamager().getName(), itemFrame.getLocation(), itemFrame.getItem().getType(), (BlockData)null);
               } else {
                  String reason = (String)this.probablyCache.getIfPresent(e.getDamager());
                  if (reason != null) {
                     this.api.logInteraction("#" + e.getDamager().getName() + "-" + reason, itemFrame.getLocation());
                     this.api.logRemoval("#" + e.getDamager().getName() + "-" + reason, itemFrame.getLocation(), itemFrame.getItem().getType(), (BlockData)null);
                  } else if (section.getBoolean("disable-unknown")) {
                     e.setCancelled(true);
                     e.setDamage(0.0D);
                     Util.broadcastNearPlayers(e.getEntity().getLocation(), section.getString("alert"));
                  }
               }

            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.LOWEST
   )
   public void onEntityHitByProjectile(EntityDamageByEntityEvent e) {
      Entity var3;
      if ((var3 = e.getDamager()) instanceof Projectile) {
         Projectile projectile = (Projectile)var3;
         String entityType = e.getDamager().getType().name().toLowerCase(Locale.ROOT);
         if (entityType.contains("wind_charge") || entityType.contains("breeze_wind_charge")) {
            return;
         }

         ProjectileSource var6;
         if ((var6 = projectile.getShooter()) instanceof Player) {
            Player player = (Player)var6;
            this.probablyCache.put(e.getEntity(), player.getName());
            return;
         }

         String reason = (String)this.probablyCache.getIfPresent(e.getDamager());
         if (reason != null) {
            this.probablyCache.put(e.getEntity(), reason);
            return;
         }

         this.probablyCache.put(e.getEntity(), e.getDamager().getName());
      }

   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onBlockIgnite(BlockIgniteEvent e) {
      String sourceFromCache;
      if (e.getIgnitingEntity() != null) {
         sourceFromCache = e.getIgnitingEntity().getType().name().toLowerCase(Locale.ROOT);
         if (sourceFromCache.contains("wind_charge") || sourceFromCache.contains("breeze_wind_charge")) {
            return;
         }

         if (e.getIgnitingEntity().getType() == EntityType.PLAYER) {
            this.probablyCache.put(e.getBlock().getLocation(), e.getPlayer().getName());
            return;
         }

         String sourceFromCache = (String)this.probablyCache.getIfPresent(e.getIgnitingEntity());
         if (sourceFromCache != null) {
            this.probablyCache.put(e.getBlock().getLocation(), sourceFromCache);
            return;
         }

         Entity var5;
         if ((var5 = e.getIgnitingEntity()) instanceof Projectile) {
            Projectile projectile = (Projectile)var5;
            if (((Projectile)e.getIgnitingEntity()).getShooter() != null) {
               ProjectileSource shooter = projectile.getShooter();
               if (shooter instanceof Player) {
                  Player player = (Player)shooter;
                  this.probablyCache.put(e.getBlock().getLocation(), player.getName());
                  return;
               }
            }
         }
      }

      if (e.getIgnitingBlock() != null) {
         sourceFromCache = (String)this.probablyCache.getIfPresent(e.getIgnitingBlock().getLocation());
         if (sourceFromCache != null) {
            this.probablyCache.put(e.getBlock().getLocation(), sourceFromCache);
            return;
         }
      }

      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "fire");
      if (section.getBoolean("enable", true)) {
         if (section.getBoolean("disable-unknown", true)) {
            e.setCancelled(true);
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onBlockBurn(BlockBurnEvent e) {
      ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "fire");
      if (section.getBoolean("enable", true)) {
         if (e.getIgnitingBlock() != null) {
            String sourceFromCache = (String)this.probablyCache.getIfPresent(e.getIgnitingBlock().getLocation());
            if (sourceFromCache != null) {
               this.probablyCache.put(e.getBlock().getLocation(), sourceFromCache);
               this.api.logRemoval("#fire-" + (String)this.probablyCache.getIfPresent(e.getIgnitingBlock().getLocation()), e.getBlock().getLocation(), e.getBlock().getType(), e.getBlock().getBlockData());
            } else if (section.getBoolean("disable-unknown", true)) {
               e.setCancelled(true);
               Util.broadcastNearPlayers(e.getIgnitingBlock().getLocation(), section.getString("alert"));
            }
         }

      }
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.LOWEST
   )
   public void onBombHit(ProjectileHitEvent e) {
      if ((e.getHitEntity() instanceof ExplosiveMinecart || e.getEntityType() == EntityType.ENDER_CRYSTAL) && e.getEntity().getShooter() != null && e.getEntity().getShooter() instanceof Player && e.getHitEntity() != null) {
         String sourceFromCache = (String)this.probablyCache.getIfPresent(e.getEntity());
         if (sourceFromCache != null) {
            this.probablyCache.put(e.getHitEntity(), sourceFromCache);
         } else {
            ProjectileSource var4;
            if (e.getEntity().getShooter() != null && (var4 = e.getEntity().getShooter()) instanceof Player) {
               Player shooter = (Player)var4;
               this.probablyCache.put(e.getHitEntity(), shooter.getName());
            }
         }
      }

   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onExplode(EntityExplodeEvent e) {
      Entity entity = e.getEntity();
      List<Block> blockList = e.blockList();
      if (!blockList.isEmpty()) {
         String entityType = e.getEntityType().name().toLowerCase(Locale.ROOT);
         if (!entityType.contains("wind_charge") && !entityType.contains("breeze_wind_charge")) {
            List<Entity> pendingRemoval = new ArrayList();
            String entityName = e.getEntityType().name().toLowerCase(Locale.ROOT);
            ConfigurationSection section = Util.bakeConfigSection(this.getConfig(), "entity-explosion");
            if (section.getBoolean("enable", true)) {
               String track = (String)this.probablyCache.getIfPresent(entity);
               Cache var10001;
               if (!(entity instanceof TNTPrimed) && !(entity instanceof EnderCrystal)) {
                  Block block;
                  Iterator var28;
                  if (entity instanceof Creeper) {
                     Creeper creeper = (Creeper)entity;
                     if (track != null) {
                        Iterator var30 = blockList.iterator();

                        while(var30.hasNext()) {
                           Block block = (Block)var30.next();
                           this.api.logRemoval(track, block.getLocation(), block.getType(), block.getBlockData());
                        }
                     } else {
                        LivingEntity creeperTarget = creeper.getTarget();
                        if (creeperTarget == null) {
                           if (!section.getBoolean("disable-unknown")) {
                              return;
                           }

                           e.blockList().clear();
                           e.getEntity().remove();
                           Util.broadcastNearPlayers(e.getLocation(), section.getString("alert"));
                           return;
                        }

                        var28 = blockList.iterator();

                        while(var28.hasNext()) {
                           block = (Block)var28.next();
                           this.api.logRemoval("#creeper-" + creeperTarget.getName(), block.getLocation(), block.getType(), block.getBlockData());
                           this.probablyCache.put(block.getLocation(), "#creeper-" + creeperTarget.getName());
                        }
                     }

                  } else if (entity instanceof Fireball) {
                     if (track != null) {
                        String reason = "#fireball-" + track;
                        var28 = blockList.iterator();

                        while(var28.hasNext()) {
                           block = (Block)var28.next();
                           this.api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                           this.probablyCache.put(block.getLocation(), reason);
                        }

                        pendingRemoval.add(entity);
                     } else if (section.getBoolean("disable-unknown")) {
                        e.blockList().clear();
                        e.getEntity().remove();
                        Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
                     }

                     var10001 = this.probablyCache;
                     pendingRemoval.forEach(var10001::invalidate);
                  } else if (entity instanceof ExplosiveMinecart) {
                     boolean isLogged = false;
                     Location blockCorner = entity.getLocation().clone().subtract(0.5D, 0.0D, 0.5D);
                     Iterator var15 = this.probablyCache.asMap().entrySet().iterator();

                     while(var15.hasNext()) {
                        Entry<Object, String> entry = (Entry)var15.next();
                        Object var17;
                        if ((var17 = entry.getKey()) instanceof Location) {
                           Location loc = (Location)var17;
                           if (loc.getWorld().equals(blockCorner.getWorld()) && loc.distance(blockCorner) < 1.0D) {
                              Iterator var19 = blockList.iterator();

                              while(var19.hasNext()) {
                                 Block block = (Block)var19.next();
                                 this.api.logRemoval("#tntminecart-" + (String)entry.getValue(), block.getLocation(), block.getType(), block.getBlockData());
                                 this.probablyCache.put(block.getLocation(), "#tntminecart-" + (String)entry.getValue());
                              }

                              isLogged = true;
                              break;
                           }
                        }
                     }

                     if (!isLogged) {
                        if (this.probablyCache.getIfPresent(entity) != null) {
                           Object var33 = this.probablyCache.getIfPresent(entity);
                           String reason = "#tntminecart-" + (String)var33;
                           Iterator var34 = blockList.iterator();

                           while(var34.hasNext()) {
                              Block block = (Block)var34.next();
                              this.api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                              this.probablyCache.put(block.getLocation(), reason);
                           }

                           pendingRemoval.add(entity);
                        } else if (section.getBoolean("disable-unknown")) {
                           e.blockList().clear();
                           Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
                        }
                     }

                     var10001 = this.probablyCache;
                     pendingRemoval.forEach(var10001::invalidate);
                  } else {
                     Entity var13;
                     if ((track == null || track.isEmpty()) && (var13 = e.getEntity()) instanceof Mob) {
                        Mob mob = (Mob)var13;
                        if (((Mob)e.getEntity()).getTarget() != null) {
                           track = mob.getTarget().getName();
                        }
                     }

                     if (track == null || track.isEmpty()) {
                        EntityDamageEvent cause = e.getEntity().getLastDamageCause();
                        if (cause != null && cause instanceof EntityDamageByEntityEvent) {
                           EntityDamageByEntityEvent entityDamageByEntityEvent = (EntityDamageByEntityEvent)cause;
                           String var10000 = e.getEntity().getName();
                           track = "#" + var10000 + "-" + entityDamageByEntityEvent.getDamager().getName();
                        }
                     }

                     if (track != null && !track.isEmpty()) {
                        var28 = e.blockList().iterator();

                        while(var28.hasNext()) {
                           block = (Block)var28.next();
                           this.api.logRemoval(track, block.getLocation(), block.getType(), block.getBlockData());
                        }
                     } else if (section.getBoolean("disable-unknown")) {
                        e.blockList().clear();
                        e.getEntity().remove();
                        Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
                     }

                  }
               } else {
                  if (track != null) {
                     String reason = "#" + entityName + "-" + track;
                     Iterator var11 = blockList.iterator();

                     while(var11.hasNext()) {
                        Block block = (Block)var11.next();
                        this.api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                        this.probablyCache.put(block.getLocation(), reason);
                     }

                     pendingRemoval.add(entity);
                  } else {
                     if (!section.getBoolean("disable-unknown", true)) {
                        return;
                     }

                     e.blockList().clear();
                     e.getEntity().remove();
                     Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
                  }

                  var10001 = this.probablyCache;
                  pendingRemoval.forEach(var10001::invalidate);
               }
            }
         }
      }
   }
}
