package com.jeepy.angelicgraves.chest;

import com.jeepy.angelicgraves.gui.DeathChestGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.HashMap;
import java.util.Map;

public class ChestSpawner implements Listener {
    private final Plugin plugin;
    private final Map<Location, DeathChestGUI> graveChests = new HashMap<>();

    public ChestSpawner(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void spawnChest(ItemStack[] items, Location location) {
        Location safeLocation = findSafeLocation(location);
        Block block = safeLocation.getBlock();
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        chest.setCustomName("§6Death Chest");
        chest.update();

        DeathChestGUI gui = new DeathChestGUI(items);
        graveChests.put(safeLocation, gui);

        Bukkit.getLogger().info("Death chest spawned and added to map at: " + safeLocation);
        Bukkit.getLogger().info("Death chest spawned at: " + safeLocation);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        Chest chest = (Chest) block.getState();
        Bukkit.getLogger().info("Chest interacted: " + chest.getLocation());
        Bukkit.getLogger().info("Chest name: " + chest.getCustomName());

        if (chest.getCustomName() == null || !chest.getCustomName().equals("§6Death Chest")) return;

        event.setCancelled(true);
        DeathChestGUI gui = graveChests.get(block.getLocation());
        Bukkit.getLogger().info("GUI found: " + (gui != null));

        if (gui != null) {
            gui.openInventory(event.getPlayer());
            Bukkit.getLogger().info("Opened death chest GUI for: " + event.getPlayer().getName());
        } else {
            Bukkit.getLogger().warning("GUI not found for chest at: " + block.getLocation());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals("§6Death Chest")) return;

        Location chestLocation = null;
        for (Map.Entry<Location, DeathChestGUI> entry : graveChests.entrySet()) {
            if (entry.getValue().getInventory().equals(event.getInventory())) {
                chestLocation = entry.getKey();
                break;
            }
        }

        if (chestLocation != null) {
            final Location finalChestLocation = chestLocation;
            Bukkit.getScheduler().runTask(plugin, () -> {
                finalChestLocation.getBlock().setType(Material.AIR);
                graveChests.remove(finalChestLocation);
                Bukkit.getLogger().info("Removed chest at: " + finalChestLocation);
            });
        }
    }

    private Location findSafeLocation(Location original) {
        Location loc = original.clone();
        if (loc.getBlock().getType() != Material.AIR) {
            for (int y = 0; y < 2; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Location check = loc.clone().add(x, y, z);
                        if (check.getBlock().getType() == Material.AIR) {
                            return check;
                        }
                    }
                }
            }
        }
        return loc;
    }
}