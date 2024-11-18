package com.jeepy.angelicgraves.chest;

import com.jeepy.angelicgraves.config.GraveConfig;
import com.jeepy.angelicgraves.event.PlayerDeathEventListener;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ChestInfo {
    private static final AtomicInteger HOLOGRAM_COUNTER = new AtomicInteger(0);

    private final UUID playerUUID;
    private final String causeOfDeath;
    private final Location chestLocation;
    private final long expirationTime;
    private final PlayerDeathEventListener listener;
    private final GraveConfig graveConfig;

    private Hologram hologram;
    private double haloAngle = 0;
    private BukkitTask updateTask;
    private BukkitTask particleTask;

    private boolean isRemoved = false;

    public ChestInfo(UUID playerUUID, String causeOfDeath, Location chestLocation,
                     long expirationTime, PlayerDeathEventListener listener,
                     GraveConfig graveConfig) {
        this.playerUUID = playerUUID;
        this.causeOfDeath = causeOfDeath;
        this.chestLocation = chestLocation;
        this.expirationTime = expirationTime;
        this.listener = listener;
        this.graveConfig = graveConfig;

        createHologram();
        startUpdateTask();
        startParticleTask();
    }

    private void createHologram() {
        String hologramName = "deathChest" + HOLOGRAM_COUNTER.incrementAndGet();
        hologram = DHAPI.createHologram(hologramName, chestLocation.clone().add(0.5, 3.3, 0.5));
        updateHologram();
    }

    public void removeHologram() {
        if (hologram != null) {
            hologram.delete();
            Bukkit.getLogger().info("Hologram has been deleted.");
        }
    }

    private void startUpdateTask() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Angelicgraves");
        if (plugin != null) {
            updateTask = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::updateHologram,
                    0L,
                    20L
            );
        }
    }

    public void updateHologram() {
        if (isRemoved) return;

        long timeLeft = (expirationTime - System.currentTimeMillis()) / 1000;
        if (timeLeft <= 0) {
            removeChest();
            return;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        String playerName = player.getName() != null ? player.getName() : "Unknown Player";

        long minutes = timeLeft / 60;
        long seconds = timeLeft % 60;

        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + "▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂");
        lines.add(ChatColor.DARK_RED + "☠ " + ChatColor.GOLD + "❈ " +
                ChatColor.RED + playerName + "'s Grave" +
                ChatColor.GOLD + " ❈");

        String deathIcon = getDeathIcon(causeOfDeath);
        lines.add(ChatColor.GRAY + deathIcon + " " +
                ChatColor.WHITE + "Cause: " +
                ChatColor.YELLOW + formatDeathMessage(causeOfDeath, playerName));

        lines.add(ChatColor.GRAY + "⌚ " +
                ChatColor.WHITE + "Expires in: " +
                ChatColor.AQUA + String.format("%02d:%02d", minutes, seconds));

        lines.add(ChatColor.GREEN + "» " +
                ChatColor.WHITE + "Right-click to collect items" +
                ChatColor.GREEN + " «");

        lines.add(ChatColor.GRAY + "▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂▂");

        DHAPI.setHologramLines(hologram, lines);
    }

    private String formatDeathMessage(String causeOfDeath, String playerName) {
        String cleanMessage = ChatColor.stripColor(causeOfDeath)
                .replace(playerName, "")
                .trim();

        cleanMessage = cleanMessage.replaceFirst("^was ", "")
                .replaceFirst("^got ", "")
                .replaceFirst("^has ", "");

        if (!cleanMessage.isEmpty()) {
            cleanMessage = cleanMessage.substring(0, 1).toUpperCase() +
                    cleanMessage.substring(1);
        }
        return cleanMessage;
    }

    public void removeChest() {
        if (isRemoved) return;
        isRemoved = true;
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (particleTask != null) {
            particleTask.cancel();
        }
        if (hologram != null) {
            hologram.delete();
        }

        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("Angelicgraves"), () -> {
            Block block = chestLocation.getBlock();
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
            listener.cleanupChestData(chestLocation);
            Bukkit.getLogger().info("Death chest at " + chestLocation + " has been removed.");
        });
    }

    private void spawnParticles() {
        World world = chestLocation.getWorld();
        if (world != null) {
            if (graveConfig.areSoulParticlesEnabled()) {
                world.spawnParticle(Particle.SOUL,
                        chestLocation.clone().add(0.5, 1, 0.5),
                        10, 0.5, 0.5, 0.5, 0.01);
            }
            if (graveConfig.areHaloParticlesEnabled()) {
                Location haloLocation = chestLocation.clone().add(0.5, 2.5, 0.5);
                double radius = 0.3;
                int points = 20;
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points + haloAngle;
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location particleLocation = haloLocation.clone().add(x, 0, z);
                    world.spawnParticle(Particle.END_ROD,
                            particleLocation,
                            1, 0, 0, 0, 0);
                }
                haloAngle += 0.1;
                if (haloAngle >= 2 * Math.PI) {
                    haloAngle = 0;
                }
            }
        }
    }

    private void startParticleTask() {
        if (graveConfig.areSoulParticlesEnabled() || graveConfig.areHaloParticlesEnabled()) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Angelicgraves");
            if (plugin != null) {
                particleTask = Bukkit.getScheduler().runTaskTimer(
                        plugin,
                        this::spawnParticles,
                        0L,
                        10L
                );
            }
        }
    }

    private String getDeathIcon(String cause) {
        cause = cause.toLowerCase();
        if (cause.contains("fall")) return "↯";
        if (cause.contains("shot") || cause.contains("arrow")) return "➹";
        if (cause.contains("drowned")) return "≋";
        if (cause.contains("explosion") || cause.contains("blew up")) return "✧";
        if (cause.contains("fire") || cause.contains("burn")) return "🔥";
        if (cause.contains("lava")) return "♨";
        if (cause.contains("magic")) return "✷";
        if (cause.contains("starved")) return "⚠";
        if (cause.contains("suffocate")) return "▣";
        if (cause.contains("void")) return "⊗";
        if (cause.contains("wither")) return "☠";
        if (cause.contains("slain") || cause.contains("killed")) return "⚔";
        return "✞";
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public String getCauseOfDeath() {
        return causeOfDeath;
    }
}