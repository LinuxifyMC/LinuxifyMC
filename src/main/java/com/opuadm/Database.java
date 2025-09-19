package com.opuadm;

import org.bukkit.entity.Player;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

@SuppressWarnings("SqlSourceToSinkFlow")
public class Database {
    private final LinuxifyMC plugin;
    private Connection connection;

    private static final String CREATE_PLAYER_TABLE =
            "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "username TEXT NOT NULL, " +
                    "fs_data TEXT, " +
                    "last_updated INTEGER)";

    public Database(LinuxifyMC plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new RuntimeException("Could not create plugin directory");
            }

            String dbPath = new File(dataFolder,
                    plugin.getConfig().getString("database.filename", "linuxifymc.db")).getAbsolutePath();

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // SQLite optimizations
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("PRAGMA journal_mode=WAL");
                stmt.executeUpdate("PRAGMA synchronous=NORMAL");
                stmt.executeUpdate("PRAGMA cache_size=10000");
                stmt.executeUpdate("PRAGMA temp_store=memory");
                stmt.executeUpdate(CREATE_PLAYER_TABLE);
            }

            plugin.getLogger().info("Database initialized at: " + dbPath);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database initialization failed", e);
            throw new RuntimeException("Database setup failed", e);
        }
    }

    public List<List<Object>> query(String sql, Object... params) {
        List<List<Object>> results = new ArrayList<>();

        try {
            ensureConnection();

            if (sql.trim().toUpperCase().startsWith("SELECT")) {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    setParameters(stmt, params);
                    try (ResultSet rs = stmt.executeQuery()) {
                        results = resultSetToList(rs);
                    }
                }
            } else {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    setParameters(stmt, params);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Query failed: " + sql, e);
        }

        return results;
    }

    public void saveData(Player player, String fsData) {
        query("INSERT OR REPLACE INTO player_data (uuid, username, fs_data, last_updated) VALUES (?, ?, ?, ?)",
                player.getUniqueId().toString(), player.getName(), fsData, System.currentTimeMillis());
    }

    public String loadFSData(UUID playerUUID) {
        List<List<Object>> results = query("SELECT fs_data FROM player_data WHERE uuid = ?",
                playerUUID.toString());

        if (!results.isEmpty() && !results.getFirst().isEmpty()) {
            Object data = results.getFirst().getFirst();
            return data != null ? data.toString() : null;
        }
        return null;
    }

    private void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            initializeDatabase();
        }
    }

    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    private List<List<Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<List<Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();

        while (rs.next()) {
            List<Object> row = new ArrayList<>(cols);
            for (int i = 1; i <= cols; i++) {
                row.add(rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Database connection closed");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing database", e);
            }
        }
    }

    public Connection getConnection() throws SQLException {
        ensureConnection();
        return connection;
    }
}