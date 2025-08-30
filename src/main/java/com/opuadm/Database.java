// those who database
package com.opuadm;

import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class Database {
    private final LinuxifyMC plugin;
    public Connection connection;
    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "username TEXT NOT NULL, " +
                    "fs_data TEXT, " +
                    "last_updated INTEGER)";

    private static final String SQL_SELECT_ALL = "SELECT * FROM player_data";
    private static final String SQL_SELECT_BY_UUID = "SELECT * FROM player_data WHERE uuid = ?";
    private static final String SQL_SELECT_BY_NAME = "SELECT * FROM player_data WHERE username = ?";
    private static final String SQL_SELECT_FS_DATA = "SELECT fs_data FROM player_data WHERE uuid = ?";

    public Database(LinuxifyMC plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    public List<List<Object>> query(String type, Object... params) {
        String sql;
        switch (type) {
            case "SELECT_ALL":
                sql = SQL_SELECT_ALL;
                break;
            case "SELECT_BY_UUID":
                sql = SQL_SELECT_BY_UUID;
                break;
            case "SELECT_BY_NAME":
                sql = SQL_SELECT_BY_NAME;
                break;
            case "SELECT_FS_DATA":
                sql = SQL_SELECT_FS_DATA;
                break;
            default:
                plugin.getLogger().severe("Unknown query type: " + type);
                return Collections.emptyList();
        }

        try {
            if (connection == null || connection.isClosed()) {
                initializeDatabase();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB init failed: " + e.getMessage());
            return Collections.emptyList();
        }

        List<List<Object>> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(cols);
                    for (int i = 1; i <= cols; i++) {
                        row.add(rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Query execution failed: " + e.getMessage());
        }
        return results;
    }

    private void initializeDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                if (!dataFolder.mkdirs()) {
                    plugin.getLogger().severe("Could not create plugin directory: " + dataFolder.getAbsolutePath());
                    return;
                }
            }

            String dbFileName = plugin.getConfig().getString("database.filename", "linuxifymc.db");
            File dbFile = new File(dataFolder, dbFileName);
            String dbPath = dbFile.getAbsolutePath();

            plugin.getLogger().info("Using SQLite database at: " + dbPath);

            Class.forName("org.sqlite.JDBC");

            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                String journalMode = plugin.getConfig().getString("database.journal_mode", "WAL");
                int synchronous = plugin.getConfig().getInt("database.synchronous", 1);
                int autoVacuum = plugin.getConfig().getInt("database.auto_vacuum", 1);
                int timeout = plugin.getConfig().getInt("database.timeout", 30) * 1000;

                stmt.execute("PRAGMA journal_mode = " + journalMode);
                stmt.execute("PRAGMA synchronous = " + synchronous);
                stmt.execute("PRAGMA auto_vacuum = " + autoVacuum);
                stmt.execute("PRAGMA busy_timeout = " + timeout);
            }

            plugin.getLogger().info("SQLite database file created at: " + dbPath);

            createTables();

        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().severe("Database initialization failed: " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                plugin.getLogger().severe(ste.toString());
            }
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
            plugin.getLogger().info("Database tables created successfully");
        }
    }

    public void saveData(Player player, String fsData) {
        try {
            if (connection == null || connection.isClosed()) {
                initializeDatabase();
            }

            String sql = "INSERT OR REPLACE INTO player_data (uuid, username, fs_data, last_updated) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, player.getName());
                stmt.setString(3, fsData);
                stmt.setLong(4, System.currentTimeMillis());
                int rowsAffected = stmt.executeUpdate();
                plugin.getLogger().info("Saved data for player " + player.getName() + " (" + rowsAffected + " rows affected)");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save data for player " + player.getName() + ": " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                plugin.getLogger().severe(ste.toString());
            }
        }
    }

    public String loadFSData(UUID playerUUID) {
        try {
            if (connection == null || connection.isClosed()) {
                initializeDatabase();
            }

            String sql = "SELECT fs_data FROM player_data WHERE uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String data = rs.getString("fs_data");
                        plugin.getLogger().info("Loaded data for UUID: " + playerUUID);
                        return data;
                    } else {
                        plugin.getLogger().info("No data found for UUID: " + playerUUID);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load data for UUID " + playerUUID + ": " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                plugin.getLogger().severe(ste.toString());
            }
        }
        return null;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not close database: " + e.getMessage());
        }
    }
}