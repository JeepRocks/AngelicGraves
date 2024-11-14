package com.jeepy.angelicgraves.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class DeathChestGUI {
    private final Inventory inventory;

    public DeathChestGUI(ItemStack[] items) {
        this.inventory = Bukkit.createInventory(null, 54, "§6Death Chest");
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                inventory.addItem(item);
            }
        }
    }

    public void openInventory(Player player) {
        player.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }
}