package com.jeepy.angelicgraves.event;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jeepy.angelicgraves.chest.ChestInfo;
import com.jeepy.angelicgraves.config.GraveConfig;
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

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerDeathEventListener implements Listener {
    private final Map<Location, Queue<ItemStack>> chestQueues = new ConcurrentHashMap<>();
    private final Map<Location, ChestInfo> chestInfos = new ConcurrentHashMap<>();
    private final GraveConfig graveConfig;
    private final Map<Location, BukkitTask> queueProcessingTasks = new HashMap<>();
    private final Gson gson = new Gson();

    public PlayerDeathEventListener(GraveConfig graveConfig) {
        this.graveConfig = graveConfig;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location location = player.getLocation();
        ItemStack[] items = player.getInventory().getContents();
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
        chestInfos.put(chestLocation, chestInfo);
        Queue<ItemStack> itemQueue = new LinkedList<>();
        chestQueues.put(chestLocation, itemQueue);

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

        long removalTimeTicks = graveConfig.getChestExpirationTime() * 20;
        Location finalChestLocation = chestLocation;
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("Angelicgraves"), () -> {
            if (chestInventory.isEmpty() && itemQueue.isEmpty()) {
                block.setType(Material.AIR);
                chestInfo.removeHologram();
                cleanupChestData(finalChestLocation);
                Bukkit.getLogger().info("Death chest at " + finalChestLocation + " has been removed after " + graveConfig.getChestExpirationTime() + " seconds.");
            }
        }, removalTimeTicks);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CHEST) {
            ChestInfo chestInfo = chestInfos.get(block.getLocation());
            if (chestInfo != null) {
                chestInfo.removeChest();
                cleanupChestData(block.getLocation());
                Bukkit.getLogger().info("Death chest at " + block.getLocation() + " has been broken and removed.");
            }
        }
    }

    public void cleanupChestData(Location chestLocation) {
        chestQueues.remove(chestLocation);
        chestInfos.remove(chestLocation);
        cancelQueueProcessing(chestLocation);

        try {
            File graveDataFile = new File(Bukkit.getPluginManager().getPlugin("Angelicgraves").getDataFolder(), "graves.json");
            saveGraves(graveDataFile);
            Bukkit.getLogger().info("Updated saved graves after removing grave at " + chestLocation);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Failed to update saved graves after removing grave at " + chestLocation + ": " + e.getMessage());
        }
    }

    private void processQueue(Inventory chestInventory, Block block, ChestInfo chestInfo, Queue<ItemStack> itemQueue) {
        if (block.getType() != Material.CHEST) {
            cancelQueueProcessing(block.getLocation());
            return;
        }

        while (!itemQueue.isEmpty() && chestInventory.firstEmpty() != -1) {
            chestInventory.addItem(itemQueue.poll());
        }

        if (chestInventory.isEmpty() && itemQueue.isEmpty()) {
            block.setType(Material.AIR);
            cleanupChestData(block.getLocation());
            chestInfo.removeChest();
            Bukkit.getLogger().info("Death chest at " + block.getLocation() + " has been removed.");
        }
    }

    public void saveGraves(File file) throws IOException {
        List<Map<String, Object>> graveData = chestInfos.entrySet().stream().map(entry -> {
            Location loc = entry.getKey();
            ChestInfo info = entry.getValue();
            Map<String, Object> graveMap = new HashMap<>();
            graveMap.put("playerUUID", info.getPlayerUUID().toString());
            graveMap.put("causeOfDeath", info.getCauseOfDeath());
            graveMap.put("chestLocation", serializeLocation(loc));
            graveMap.put("expirationTime", info.getExpirationTime());

            Queue<ItemStack> items = chestQueues.get(loc);
            if (items != null) {
                graveMap.put("items", items.stream().map(this::serializeItem).collect(Collectors.toList()));
            }
            return graveMap;
        }).collect(Collectors.toList());

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(graveData, writer);
        }
    }

    public void loadGraves(File file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            List<Map<String, Object>> graveData = gson.fromJson(reader, List.class);
            long currentTime = System.currentTimeMillis();

            for (Map<String, Object> graveMap : graveData) {
                if (validateGraveData(graveMap)) {
                    UUID playerUUID = UUID.fromString((String) graveMap.get("playerUUID"));
                    String causeOfDeath = (String) graveMap.get("causeOfDeath");
                    Location chestLocation = deserializeLocation((Map<String, Object>) graveMap.get("chestLocation"));
                    long expirationTime = ((Double) graveMap.get("expirationTime")).longValue();

                    ChestInfo chestInfo = new ChestInfo(playerUUID, causeOfDeath, chestLocation, expirationTime, this, graveConfig);
                    chestInfos.put(chestLocation, chestInfo);

                    List<Map<String, Object>> serializedItems = (List<Map<String, Object>>) graveMap.get("items");
                    Queue<ItemStack> itemQueue = serializedItems.stream()
                            .map(this::deserializeItem)
                            .collect(Collectors.toCollection(LinkedList::new));
                    chestQueues.put(chestLocation, itemQueue);

                    Block block = chestLocation.getBlock();
                    if (block.getType() == Material.CHEST) {
                        Chest chest = (Chest) block.getState();
                        Inventory chestInventory = chest.getBlockInventory();

                        scheduleQueueProcessing(block, chestInventory, chestInfo, itemQueue);

                        long timeLeft = expirationTime - currentTime;
                        if (timeLeft > 0) {
                            Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("Angelicgraves"), () -> {
                                if (chestInventory.isEmpty() && itemQueue.isEmpty()) {
                                    block.setType(Material.AIR);
                                    chestInfo.removeHologram();
                                    cleanupChestData(chestLocation);
                                    Bukkit.getLogger().info("Death chest at " + chestLocation + " has been removed.");
                                }
                            }, timeLeft / 50L);
                        } else {

                            cleanupChestData(chestLocation);
                        }
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            Bukkit.getLogger().warning("Failed to load graves: Invalid JSON format.");
        }
    }


    private boolean validateGraveData(Map<String, Object> graveMap) {
        return graveMap.containsKey("playerUUID") &&
                graveMap.containsKey("causeOfDeath") &&
                graveMap.containsKey("chestLocation") &&
                graveMap.containsKey("expirationTime");
    }

    private Map<String, Object> serializeLocation(Location location) {
        Map<String, Object> map = new HashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        return map;
    }

    private Location deserializeLocation(Map<String, Object> map) {
        World world = Bukkit.getWorld((String) map.get("world"));
        if (world == null) {
            throw new IllegalArgumentException("World not found: " + map.get("world"));
        }
        double x = (Double) map.get("x");
        double y = (Double) map.get("y");
        double z = (Double) map.get("z");
        return new Location(world, x, y, z);
    }

    private Map<String, Object> serializeItem(ItemStack item) {
        return item.serialize();
    }

    private ItemStack deserializeItem(Map<String, Object> map) {
        return ItemStack.deserialize(map);
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