package com.jeepy.angelicgraves.event;

import com.jeepy.angelicgraves.Angelicgraves;
import com.jeepy.angelicgraves.util.AngelicDatabase;
import com.jeepy.angelicgraves.chest.ChestInfo;
import com.jeepy.angelicgraves.config.GraveConfig;
import com.jeepy.angelicgraves.util.QueueManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PlayerDeathEventListener implements Listener {
    private final GraveConfig graveConfig;
    private final Map<Location, BukkitTask> queueProcessingTasks = new HashMap<>();
    private final AngelicDatabase database;
    private final QueueManager queueManager;
    private final Angelicgraves plugin;

    public PlayerDeathEventListener(GraveConfig graveConfig, AngelicDatabase database, QueueManager queueManager, Angelicgraves plugin) {
        this.graveConfig = graveConfig;
        this.database = database;
        this.queueManager = queueManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ItemStack[] items = player.getInventory().getContents();

        // Check if player has any items
        boolean hasItems = false;
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                hasItems = true;
                break;
            }
        }

        // Don't create chest if player has no items
        if (!hasItems) {
            return;
        }

        Location location = player.getLocation();
        Location chestLocation = findSafeLocation(location);
        Block block = chestLocation.getBlock();
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        Inventory chestInventory = chest.getBlockInventory();
        long expirationTime = System.currentTimeMillis() + graveConfig.getChestExpirationTime() * 1000;
        chestLocation = block.getLocation();

        if (chestLocation.getBlock().getType() != Material.CHEST) {
            Bukkit.getLogger().warning("The block at " + chestLocation + " is not a chest after placement.");
            return;
        }

        ChestInfo chestInfo = new ChestInfo(player.getUniqueId(), event.getDeathMessage(), chestLocation, expirationTime, this, graveConfig);
        plugin.registerGrave(chestLocation, chestInfo);

        Queue<ItemStack> itemQueue = new LinkedList<>();
        queueManager.addQueue(chestLocation, itemQueue);
        database.saveGrave(player.getUniqueId(), chestLocation, expirationTime, event.getDeathMessage(), itemQueue);


        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                if (chestInventory.firstEmpty() == -1) {
                    itemQueue.add(item);
                } else {
                    chestInventory.addItem(item);
                }
            }
        }

        event.getDrops().clear();
        Bukkit.getLogger().info("Death chest spawned at: " + chestLocation);

        processQueue(chestInventory, block, chestInfo, itemQueue);
        BukkitTask queueTask = scheduleQueueProcessing(block, chestInventory, chestInfo, itemQueue);
        queueProcessingTasks.put(chestLocation, queueTask);

        Location finalChestLocation = chestLocation;

        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("Angelicgraves"), () -> {
            if (chestInventory.isEmpty() && itemQueue.isEmpty()) {
                block.setType(Material.AIR);
                chestInfo.removeHologram();
                cleanupChestData(finalChestLocation);
                Bukkit.getLogger().info("Death chest at " + finalChestLocation + " has been removed after " + graveConfig.getChestExpirationTime() + " seconds.");
            }
        }, graveConfig.getChestExpirationTime() * 20);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CHEST) {
            ChestInfo chestInfo = plugin.getGrave(block.getLocation());
            if (chestInfo != null) {
                chestInfo.removeChest();
                cleanupChestData(block.getLocation());
                Bukkit.getLogger().info("Death chest at " + block.getLocation() + " has been broken and removed.");
            }
        }
    }

    public void cleanupChestData(Location chestLocation) {
        queueManager.removeQueue(chestLocation);
        plugin.unregisterGrave(chestLocation);
        cancelQueueProcessing(chestLocation);
        database.removeGrave(chestLocation);
    }

    private void processQueue(Inventory chestInventory, Block block, ChestInfo chestInfo, Queue<ItemStack> itemQueue) {
        if (block.getType() != Material.CHEST) {
            cancelQueueProcessing(block.getLocation());
            return;
        }

        Queue<ItemStack> queue = queueManager.getQueue(block.getLocation());
        if (queue != null) {
            while (!queue.isEmpty() && chestInventory.firstEmpty() != -1) {
                chestInventory.addItem(queue.poll());
            }

            if (chestInventory.isEmpty() && queue.isEmpty()) {
                block.setType(Material.AIR);
                chestInfo.removeHologram();
                chestInfo.removeChest();
                cleanupChestData(block.getLocation());
                Bukkit.getLogger().info("Death chest at " + block.getLocation() + " has been removed.");
            }
        }
    }

    private BukkitTask scheduleQueueProcessing(Block block, Inventory chestInventory, ChestInfo chestInfo, Queue<ItemStack> itemQueue) {
        if (queueProcessingTasks.containsKey(block.getLocation())) {
            Bukkit.getLogger().warning("Queue processing task is already running for location " + block.getLocation());
            return null;
        }
        return Bukkit.getScheduler().runTaskTimer(Bukkit.getPluginManager().getPlugin("Angelicgraves"), () -> {
            processQueue(chestInventory, block, chestInfo, itemQueue);
        }, 20L, 20L);
    }

    private void cancelQueueProcessing(Location location) {
        BukkitTask task = queueProcessingTasks.remove(location);
        if (task != null) {
            task.cancel();
        }
    }

    public void saveAllChestInfos() {
        for (Map.Entry<Location, ChestInfo> entry : plugin.getActiveGraves().entrySet()) {
            ChestInfo chestInfo = entry.getValue();
            database.saveGrave(chestInfo.getPlayerUUID(), chestInfo.getChestLocation(),
                    chestInfo.getExpirationTime(), chestInfo.getCauseOfDeath(),
                    queueManager.getQueue(chestInfo.getChestLocation()));
        }
    }


    public void recreateGraves(List<AngelicDatabase.GraveData> graves) {
        for (AngelicDatabase.GraveData graveData : graves) {
            Location chestLocation = graveData.location();
            Block block = chestLocation.getBlock();

            if (block.getType() != Material.CHEST) {
                block.setType(Material.CHEST);
            }

            Chest chest = (Chest) block.getState();
            Inventory chestInventory = chest.getBlockInventory();

            ChestInfo chestInfo = new ChestInfo(
                    graveData.playerUUID(),
                    graveData.causeOfDeath(),
                    chestLocation,
                    graveData.expirationTime(),
                    this,
                    graveConfig
            );

            plugin.registerGrave(chestLocation, chestInfo);

            Queue<ItemStack> itemQueue = new LinkedList<>(graveData.items());
            queueManager.addQueue(chestLocation, itemQueue);
            processQueue(chestInventory, block, chestInfo, itemQueue);

            Bukkit.getLogger().info("Recreated grave at " + chestLocation + " for player " + graveData.playerUUID());
        }
    }

    public void cancelAllTasks() {
        for (BukkitTask task : queueProcessingTasks.values()) {
            task.cancel();
        }
        queueProcessingTasks.clear();
    }

    private Location findSafeLocation(Location location) {
        Location safeLocation = location.clone();
        while (safeLocation.getBlock().getType() == Material.AIR && safeLocation.getY() > 0) {
            safeLocation.subtract(0, 1, 0);
        }
        safeLocation.add(0, 1, 0);
        if (safeLocation.getBlock().getType() != Material.AIR) {
            safeLocation.add(0, 1, 0);
        }
        return safeLocation;
    }
}