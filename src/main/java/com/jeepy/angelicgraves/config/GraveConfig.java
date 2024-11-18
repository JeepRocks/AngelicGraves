package com.jeepy.angelicgraves.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class GraveConfig {
    private final JavaPlugin plugin;
    private long chestExpirationTime;
    private boolean soulParticlesEnabled;
    private boolean haloParticlesEnabled;

    public GraveConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        chestExpirationTime = config.getLong("chestRemovalTime", 600);
        soulParticlesEnabled = config.getBoolean("soulParticlesEnabled", true);
        haloParticlesEnabled = config.getBoolean("haloParticlesEnabled", true);
    }

    public long getChestExpirationTime() {
        return chestExpirationTime;
    }

    public boolean areSoulParticlesEnabled() {
        return soulParticlesEnabled;
    }

    public boolean areHaloParticlesEnabled() {
        return haloParticlesEnabled;
    }
}