package com.jeepy.angelicgraves.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class QueueManager {
    private final JavaPlugin plugin;
    private final Map<Location, Queue<ItemStack>> queueMap;
    private final File saveFile;

    public QueueManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.queueMap = new HashMap<>();
        this.saveFile = new File(plugin.getDataFolder(), "queues.dat");
    }

    public void addQueue(Location location, Queue<ItemStack> queue) {
        queueMap.put(location, queue);
    }

    public Queue<ItemStack> getQueue(Location location) {
        return queueMap.get(location);
    }

    public void removeQueue(Location location) {
        queueMap.remove(location);
    }

    public void saveQueues() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(saveFile))) {
            Map<String, String> serializedMap = new HashMap<>();
            for (Map.Entry<Location, Queue<ItemStack>> entry : queueMap.entrySet()) {
                String locationKey = serializeLocation(entry.getKey());
                String queueValue = BukkitSerializer.itemStackQueueToBase64(entry.getValue());
                serializedMap.put(locationKey, queueValue);
            }
            oos.writeObject(serializedMap);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save queues: " + e.getMessage());
        }
    }

    public void loadQueues() {
        if (!saveFile.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(saveFile))) {
            Object obj = ois.readObject();
            if (!(obj instanceof Map<?, ?> rawMap)) {
                plugin.getLogger().severe("Saved data is not a Map");
                return;
            }

            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    Location location = deserializeLocation((String) entry.getKey());
                    Queue<ItemStack> queue = BukkitSerializer.itemStackQueueFromBase64((String) entry.getValue());
                    queueMap.put(location, queue);
                } else {
                    plugin.getLogger().warning("Invalid entry type in saved data");
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().severe("Failed to load queues: " + e.getMessage());
        }
    }

    private String serializeLocation(Location location) {
        return location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ();
    }

    private Location deserializeLocation(String serialized) {
        String[] parts = serialized.split(",");
        return new Location(Bukkit.getWorld(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
    }
}