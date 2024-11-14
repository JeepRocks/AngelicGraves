package com.jeepy.angelicgraves;

import com.jeepy.angelicgraves.chest.ChestSpawner;
import com.jeepy.angelicgraves.event.PlayerDeathEventListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Angelicgraves extends JavaPlugin {
    private ChestSpawner chestSpawner;

    @Override
    public void onEnable() {
        // Initialize ChestSpawner
        chestSpawner = new ChestSpawner(this);

        // Register both listeners
        getServer().getPluginManager().registerEvents(chestSpawner, this);
        getServer().getPluginManager().registerEvents(new PlayerDeathEventListener(chestSpawner), this);

        // Log plugin startup
        getLogger().info("AngelicGraves has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("AngelicGraves has been disabled!");
    }
}