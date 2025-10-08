package com.opuadm.linuxifymc;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
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

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("PRAGMA journal_mode=WAL");
                stmt.executeUpdate("PRAGMA synchronous=NORMAL");
                stmt.executeUpdate("PRAGMA cache_size=10000");
                stmt.executeUpdate("PRAGMA temp_store=memory");
                stmt.executeUpdate(CREATE_PLAYER_TABLE);

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fs_dirs (" +
                        "player_uuid TEXT NOT NULL, " +
                        "path TEXT NOT NULL, " +
                        "owner TEXT NOT NULL, " +
                        "group_name TEXT NOT NULL, " +
                        "permissions TEXT NOT NULL, " +
                        "PRIMARY KEY (player_uuid, path))");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fs_files (" +
                        "player_uuid TEXT NOT NULL, " +
                        "path TEXT NOT NULL, " +
                        "owner TEXT NOT NULL, " +
                        "group_name TEXT NOT NULL, " +
                        "permissions TEXT NOT NULL, " +
                        "content TEXT, " +
                        "PRIMARY KEY (player_uuid, path))");

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS fs_saves (" +
                        "player_uuid TEXT PRIMARY KEY, " +
                        "player_name TEXT NOT NULL, " +
                        "fs_version TEXT NOT NULL, " +
                        "disk_space_used INTEGER, " +
                        "disk_space_free INTEGER, " +
                        "current_dir TEXT)");
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

    @SuppressWarnings("UnusedReturnValue")
    public int executeUpdate(String sql, Object... params) {
        try {
            ensureConnection();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                setParameters(stmt, params);
                return stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Execute update failed: " + sql, e);
            return -1;
        }
    }

    public Object singleValueQuery(String sql, Object... params) {
        try {
            ensureConnection();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                setParameters(stmt, params);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject(1);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "singleValueQuery failed: " + sql, e);
        }
        return null;
    }

    @SuppressWarnings("UnusedReturnValue")
    public <T> T runInTransaction(Function<Connection, T> work) {
        try {
            ensureConnection();
            boolean oldAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (Exception ex) {
                try {
                    connection.rollback();
                } catch (SQLException rbEx) {
                    plugin.getLogger().log(Level.WARNING, "Transaction rollback failed", rbEx);
                }
                throw new RuntimeException(ex);
            } finally {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                } catch (SQLException acEx) {
                    plugin.getLogger().log(Level.WARNING, "Failed to restore autoCommit", acEx);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "runInTransaction failed to obtain connection", e);
            throw new RuntimeException(e);
        }
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

    @SuppressWarnings("unused")
    public Connection getConnection() throws SQLException {
        ensureConnection();
        return connection;
    }
}