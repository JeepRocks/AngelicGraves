package com.jeepy.angelicgraves.event;

import com.jeepy.angelicgraves.chest.ChestSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerDeathEventListener implements Listener {
    private final ChestSpawner chestSpawner;

    public PlayerDeathEventListener(ChestSpawner chestSpawner) {
        this.chestSpawner = chestSpawner;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ItemStack[] items = player.getInventory().getContents();
        Location location = player.getLocation();

        chestSpawner.spawnChest(items, location);
        event.getDrops().clear();

        Bukkit.getLogger().info("Player died: " + player.getName() + " at " + location);
    }
}