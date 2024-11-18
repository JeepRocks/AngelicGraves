package com.jeepy.angelicgraves;

import com.jeepy.angelicgraves.config.GraveConfig;
import com.jeepy.angelicgraves.event.PlayerDeathEventListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class Angelicgraves extends JavaPlugin {
    private PlayerDeathEventListener listener;
    private GraveConfig graveConfig;
    private File graveDataFile;

    @Override
    public void onEnable() {

        this.graveConfig = new GraveConfig(this);

        this.listener = new PlayerDeathEventListener(graveConfig);

        getServer().getPluginManager().registerEvents(listener, this);

        graveDataFile = new File(getDataFolder(), "graves.json");
        if (graveDataFile.exists()) {
            try {
                listener.loadGraves(graveDataFile);
                getLogger().info("Loaded saved graves from file.");
            } catch (IOException e) {
                getLogger().warning("Failed to load graves: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        try {
            listener.saveGraves(graveDataFile);
            getLogger().info("Saved graves to file.");
        } catch (IOException e) {
            getLogger().warning("Failed to save graves: " + e.getMessage());
        }

        getLogger().info("AngelicGraves has been disabled!");
    }
}