package com.leir4iks.coreprotecttnt;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class CoreProtectHook {
    private final Main plugin;
    private Method queueContainerTransactionMethod;
    private boolean isAvailable = false;

    public CoreProtectHook(Main plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            Class<?> queueClass = plugin.getApi().getClass().getSuperclass();
            this.queueContainerTransactionMethod = queueClass.getDeclaredMethod(
                    "queueContainerTransaction",
                    String.class,
                    Location.class,
                    int.class,
                    Object.class
            );
            this.queueContainerTransactionMethod.setAccessible(true);
            this.isAvailable = true;
            plugin.getLogger().info("Successfully hooked into CoreProtect's internal container logging API. Item frames will be logged as containers.");

        } catch (Exception e) {
            this.isAvailable = false;
            plugin.getLogger().warning("Could not hook into CoreProtect's internal container API. This is likely due to a CoreProtect update.");
            plugin.getLogger().warning("Item frame interactions will be logged in a simplified, safe mode. Functionality is not lost.");
        }
    }

    public void logFrameTransaction(Player player, ItemFrame frame, ItemStack before, ItemStack after) {
        if (isAvailable) {
            logWithReflection(player, frame, before, after);
        } else {
            logSafely(player, before, after, frame.getLocation());
        }
    }

    private void logWithReflection(Player player, ItemFrame frame, ItemStack before, ItemStack after) {
        try {
            String user = player.getName();
            Location location = frame.getLocation();

            int typeId = (frame.getType() == EntityType.GLOW_ITEM_FRAME ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME).getId();

            ItemStack[] beforeState = { before.getType() == Material.AIR ? null : before };
            ItemStack[] afterState = { after.getType() == Material.AIR ? null : after };
            Object[] data = new Object[]{beforeState, afterState};

            queueContainerTransactionMethod.invoke(plugin.getApi(), user, location, typeId, data);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred while logging an item frame transaction via reflection.", e);
            logSafely(player, before, after, frame.getLocation());
        }
    }

    private void logSafely(Player player, ItemStack before, ItemStack after, Location location) {
        String reason;
        if (before.getType() == Material.AIR && after.getType() != Material.AIR) {
            reason = "#item_added:" + after.getType().name().toLowerCase();
        } else if (before.getType() != Material.AIR && after.getType() == Material.AIR) {
            reason = "#item_removed:" + before.getType().name().toLowerCase();
        } else {
            reason = "#item_swapped:" + before.getType().name().toLowerCase() + "->" + after.getType().name().toLowerCase();
        }
        plugin.getApi().logInteraction(player.getName() + reason, location);
    }
}