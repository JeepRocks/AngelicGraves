package com.jeepy.angelicgraves.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.*;

public class AngelicDatabase {
    private final JavaPlugin plugin;
    private Connection connection;

    public AngelicDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/graves.db");

            // Graves table
            String gravesSql = "CREATE TABLE IF NOT EXISTS graves ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "player_uuid VARCHAR(36),"
                    + "world VARCHAR(50),"
                    + "x DOUBLE,"
                    + "y DOUBLE,"
                    + "z DOUBLE,"
                    + "expiration_time LONG,"
                    + "cause_of_death TEXT"
                    + ");";

            // Items table
            String itemsSql = "CREATE TABLE IF NOT EXISTS grave_items ("
                    + "grave_id INTEGER,"
                    + "item_data TEXT,"
                    + "FOREIGN KEY (grave_id) REFERENCES graves(id)"
                    + ");";

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(gravesSql);
                stmt.execute(itemsSql);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    public void saveGrave(UUID playerUUID, Location location, long expirationTime, String causeOfDeath, Queue<ItemStack> itemQueue) {
        String graveSql = "INSERT INTO graves (player_uuid, world, x, y, z, expiration_time, cause_of_death) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(graveSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, location.getWorld().getName());
            pstmt.setDouble(3, location.getX());
            pstmt.setDouble(4, location.getY());
            pstmt.setDouble(5, location.getZ());
            pstmt.setLong(6, expirationTime);
            pstmt.setString(7, causeOfDeath);
            pstmt.executeUpdate();

            // Get the generated grave ID
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int graveId = rs.getInt(1);

                // Save items
                String itemSql = "INSERT INTO grave_items (grave_id, item_data) VALUES (?, ?)";
                try (PreparedStatement itemStmt = connection.prepareStatement(itemSql)) {
                    for (ItemStack item : itemQueue) {
                        itemStmt.setInt(1, graveId);
                        itemStmt.setString(2, itemStackToBase64(item));
                        itemStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save grave: " + e.getMessage());
        }
    }

    public List<GraveData> loadGraves() {
        List<GraveData> graves = new ArrayList<>();
        String sql = "SELECT g.*, gi.item_data FROM graves g LEFT JOIN grave_items gi ON g.id = gi.grave_id WHERE g.expiration_time > ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, System.currentTimeMillis());
            ResultSet rs = pstmt.executeQuery();

            Map<Integer, List<ItemStack>> graveItems = new HashMap<>();

            while (rs.next()) {
                int graveId = rs.getInt("id");
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                Location location = new Location(
                        Bukkit.getWorld(rs.getString("world")),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z")
                );
                long expirationTime = rs.getLong("expiration_time");
                String causeOfDeath = rs.getString("cause_of_death");

                String itemData = rs.getString("item_data");
                if (itemData != null) {
                    graveItems.computeIfAbsent(graveId, k -> new ArrayList<>())
                            .add(itemStackFromBase64(itemData));
                }

                graves.add(new GraveData(playerUUID, location, expirationTime, causeOfDeath,
                        graveItems.getOrDefault(graveId, new ArrayList<>())));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load graves: " + e.getMessage());
        }
        return graves;
    }

    public void removeGrave(Location location) {
        String sql = "DELETE FROM graves WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, location.getWorld().getName());
            pstmt.setDouble(2, location.getX());
            pstmt.setDouble(3, location.getY());
            pstmt.setDouble(4, location.getZ());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove grave: " + e.getMessage());
        }
    }

    private String itemStackToBase64(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stack.", e);
        }
    }

    private ItemStack itemStackFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load item stack.", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database connection: " + e.getMessage());
        }
    }

    public record GraveData(UUID playerUUID, Location location, long expirationTime,
                            String causeOfDeath, List<ItemStack> items) {
    }
}