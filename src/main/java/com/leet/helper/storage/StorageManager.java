package com.leet.helper.storage;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StorageManager {

    private final Logger logger;
    private Connection connection;
    private final Map<String, Map<String, Map<UUID, Long>>> runtime = new HashMap<>();

    public StorageManager(File dataFolder, Logger logger) {
        this.logger = logger;
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTable();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize SQLite", e);
        }
    }

    private void createTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS kv_store (" +
                "feature_id TEXT NOT NULL, " +
                "key TEXT NOT NULL, " +
                "uuid TEXT NOT NULL, " +
                "value TEXT, " +
                "updated_at INTEGER, " +
                "PRIMARY KEY (feature_id, key, uuid)" +
                ")"
            );
        }
    }

    // Runtime (in-memory) methods

    public void setRuntime(String featureId, String key, UUID uuid, long value) {
        runtime.computeIfAbsent(featureId, k -> new HashMap<>())
               .computeIfAbsent(key, k -> new HashMap<>())
               .put(uuid, value);
    }

    public long getRuntime(String featureId, String key, UUID uuid, long defaultValue) {
        Map<String, Map<UUID, Long>> featureMap = runtime.get(featureId);
        if (featureMap == null) return defaultValue;
        Map<UUID, Long> keyMap = featureMap.get(key);
        if (keyMap == null) return defaultValue;
        return keyMap.getOrDefault(uuid, defaultValue);
    }

    // Per-player feature toggles (persistent). Absent value = enabled.

    public Boolean getUserToggle(String featureId, UUID uuid) {
        String val = getPersistent(featureId, "user-toggle", uuid);
        if (val == null) return null;
        return Boolean.parseBoolean(val);
    }

    public void setUserToggle(String featureId, UUID uuid, boolean enabled) {
        setPersistent(featureId, "user-toggle", uuid, String.valueOf(enabled));
    }

    // Persistent (SQLite) methods

    public void setPersistent(String featureId, String key, UUID uuid, String value) {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO kv_store (feature_id, key, uuid, value, updated_at) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(feature_id, key, uuid) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at")) {
            ps.setString(1, featureId);
            ps.setString(2, key);
            ps.setString(3, uuid.toString());
            ps.setString(4, value);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to set persistent value", e);
        }
    }

    public String getPersistent(String featureId, String key, UUID uuid) {
        if (connection == null) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM kv_store WHERE feature_id=? AND key=? AND uuid=?")) {
            ps.setString(1, featureId);
            ps.setString(2, key);
            ps.setString(3, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get persistent value", e);
        }
        return null;
    }

    public void deletePersistent(String featureId, String key, UUID uuid) {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM kv_store WHERE feature_id=? AND key=? AND uuid=?")) {
            ps.setString(1, featureId);
            ps.setString(2, key);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete persistent value", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to close SQLite connection", e);
        }
    }
}
