package com.jeepy.angelicgraves;

import com.jeepy.angelicgraves.chest.ChestInfo;
import com.jeepy.angelicgraves.util.AngelicDatabase;
import com.jeepy.angelicgraves.config.GraveConfig;
import com.jeepy.angelicgraves.event.PlayerDeathEventListener;
import com.jeepy.angelicgraves.util.QueueManager;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Angelicgraves extends JavaPlugin {
    private PlayerDeathEventListener listener;
    private GraveConfig graveConfig;
    private AngelicDatabase database;
    private QueueManager queueManager;
    private Map<Location, ChestInfo> activeGraves = new HashMap<>();

    @Override
    public void onEnable() {
        // Clean up any existing graves first
        cleanupExistingGraves();

        this.graveConfig = new GraveConfig(this);
        database = new AngelicDatabase(this);
        List<AngelicDatabase.GraveData> graves = database.loadGraves();
        queueManager = new QueueManager(this);
        queueManager.loadQueues();

        // Pass the loaded graves to the listener
        this.listener = new PlayerDeathEventListener(graveConfig, database, queueManager, this);
        listener.recreateGraves(graves);
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        // Clean up all graves
        cleanupExistingGraves();

        if (listener != null) {
            listener.saveAllChestInfos();
            listener.cancelAllTasks();
        }
        if (database != null) {
            database.close();
        }
        if (queueManager != null) {
            queueManager.saveQueues();
        }
        getLogger().info("AngelicGraves has been disabled!");
    }

    private void cleanupExistingGraves() {
        for (ChestInfo grave : activeGraves.values()) {
            grave.cleanupAllEffects();
        }
        activeGraves.clear();
    }

    public void registerGrave(Location location, ChestInfo chestInfo) {
        activeGraves.put(location, chestInfo);
    }

    public void unregisterGrave(Location location) {
        activeGraves.remove(location);
    }
        public ChestInfo getGrave(Location location) {
            return activeGraves.get(location);
        }

        // Get all active graves
        public Map<Location, ChestInfo> getActiveGraves() {
            return activeGraves;
        }
}