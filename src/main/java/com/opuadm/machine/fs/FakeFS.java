// Remade FakeFS
package com.opuadm.machine.fs;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import com.opuadm.Database;

public class FakeFS {
    // Variables
    private static FakeFS fs;

    public static String FS_VER = "0.1.1";

    public static Database DB;

    public static long maxDiskSpace = 768L * ConvertUnits.GB; // 768GB Disk Space Available
    public static long diskSpaceUsed = 1722 * ConvertUnits.MB; // "1.722GB" Disk Space Used (As default with only system files)
    public static long diskSpaceUsedByUserFiles = 0L; // 0 Bytes by default
    public static long totalDiskSpaceUsed = diskSpaceUsed + diskSpaceUsedByUserFiles;
    public static long diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed; // Free Disk Space

    private static final String defaultGroup = "users";
    private static final Logger logger = Logger.getLogger(FakeFS.class.getName());

    private static String CurDir = null;

    private static String plr = null;

    // Main Public for FakeFS
    public FakeFS(String playerName) {
        plr = playerName;
        CurDir = "/home/" + plr.toLowerCase();
    }

    // Generic / Misc
    public static boolean saveFS(Player player, FakeFS fs) {
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return false;
        }
        try {
            String sql = "INSERT INTO fs_saves (player_uuid, player_name, fs_version) VALUES (?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE fs_version = ?";
            String sql2 = "INSERT INTO fs_saves (player_uuid, player_name, disk_space_used, disk_space_free) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE disk_space_used = ?, disk_space_free = ?";
            DB.query(sql, player.getUniqueId().toString(), player.getName(), FS_VER, FS_VER);
            DB.query(sql2, player.getUniqueId().toString(), player.getName(), diskSpaceUsed, diskSpaceFree,
                     diskSpaceUsed, diskSpaceFree);
            return true;
        } catch (Exception e) {
            logger.warning("E: An error occurred while saving the filesystem: " + e.getMessage());
            return false;
        }
    }

    public static void upgradeFS(FakeFS fs) {
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return;
        }
        try {
            String sql = "UPDATE fs_saves SET fs_version = ? WHERE fs_version < ?";
            DB.query(sql, FS_VER, FS_VER);
            logger.info("I: Filesystem upgraded to version " + FS_VER);
        } catch (Exception e) {
            logger.warning("E: An error occurred while upgrading the filesystem: " + e.getMessage());
        }
    }

    // Setup
    public static void setupSysFiles() {
        // System Directories (From root directory)
        fs.makeDir("/sys", "root", "755");
        fs.makeDir("/dev", "root", "755");
        fs.makeDir("/etc", "root", "755");
        fs.makeDir("/boot", "root", "755");
        fs.makeDir("/proc", "root", "755");
        fs.makeDir("/root", "root", "755");
        fs.makeDir("/usr", "root", "755");
        fs.makeDir("/home", "root", "755");
        fs.makeDir("/tmp", "root", "755");
        fs.makeDir("/var", "root", "755");
        fs.makeDir("/opt", "root", "755");
        // System Directories (From /usr directory)
        fs.makeDir("/usr/bin", "root", "755");
        fs.makeDir("/usr/sbin", "root", "755");
        fs.makeDir("/usr/lib", "root", "755");
        fs.makeDir("/usr/lib64", "root", "755");
        fs.makeDir("/usr/local", "root", "755");
        // /var Directories
        fs.makeDir("/var/log", "root", "755");
        // Player Home Directory
        fs.makeDir("/home/" + plr.toLowerCase(), plr.toLowerCase(), "777");
    }

    // Player Filesystem
    public static FakeFS getPlayerFS(UUID plrUUID, String username) {
        Objects.requireNonNull(plrUUID, "E: UUID cannot be null.");
        Objects.requireNonNull(username, "E: Username cannot be null.");
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return null;
        }
        return fs;
    }

    // Get (Directories, Files, etc.)
    public static String getDir(String path) {
        try {
            if (path == null || path.isEmpty()) {
                return null;
            }
            path = path.replace("/+", "/");
            String sql = "SELECT * FROM fs_dirs WHERE path = ?";

            var result = DB.query(sql, path);
            if (result.isEmpty()) {
                logger.warning("E: Directory not found: " + path);
                return null;
            }
        } catch (Exception e) {
            logger.warning("E: An error occurred while retrieving the directory: " + e.getMessage());
            return null;
        }
        return path;
    }

    public static void setFs(FakeFS fs) {
        FakeFS.fs = fs;
    }

    public String getCurrentDir() {
        return CurDir;
    }

    // Make (Directories, Files, etc.)
    public void makeDir(String path, String owner, String perms) {
        try {
            if (path == null || path.isEmpty()) return;

            path = path.replace("/+", "/");

            String parentDir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";

            if (!parentDir.isEmpty()) {
                var parentResult = DB.query("SELECT owner, permissions FROM fs_dirs WHERE path = ?", parentDir);

                if (parentResult.isEmpty()) return;

                var parentInfo = (java.util.Map<?,?>) parentResult.getFirst();
                String parentOwner = (String) parentInfo.get("owner");
                String parentPerms = (String) parentInfo.get("permissions");

                if (hasPermissions(parentPerms, "w")) return;
            }

            var countResult = DB.query("SELECT COUNT(*) AS cnt FROM fs_dirs WHERE path = ?", path);
            var countInfo = (java.util.Map<?,?>) countResult.getFirst();
            long dirCount = ((Number) countInfo.get("cnt")).longValue();

            if (dirCount > 0) return;

            DB.query("INSERT INTO fs_dirs (path, owner, group_name, permissions) VALUES (?, ?, ?, ?)", path, owner, defaultGroup, perms);

            changePermissions(path, perms);
        } catch (Exception ignored) {}
    }

    public void makeFile(String path, String owner, String perms, String content) {
        try {
            if (path == null || path.isEmpty()) return;

            path = path.replace("/+", "/");

            String parentDir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";

            if (!parentDir.isEmpty()) {
                var parentResult = DB.query("SELECT owner, permissions FROM fs_dirs WHERE path = ?", parentDir);

                if (parentResult.isEmpty()) return;

                var parentInfo = (java.util.Map<?,?>) parentResult.getFirst();
                String parentOwner = (String) parentInfo.get("owner");
                String parentPerms = (String) parentInfo.get("permissions");

                if (hasPermissions(parentPerms, "w")) return;
            }

            if (content.length() > diskSpaceFree) return;

            var countResult = DB.query("SELECT COUNT(*) AS cnt FROM fs_files WHERE path = ?", path);
            var countInfo = (java.util.Map<?,?>) countResult.getFirst();
            long fileCount = ((Number) countInfo.get("cnt")).longValue();
            if (fileCount > 0) return;

            DB.query("INSERT INTO fs_files (path, owner, group_name, permissions, content) VALUES (?, ?, ?, ?, ?)", path, owner, defaultGroup, perms, content);

            changePermissions(path, perms);
        } catch (Exception ignored) {}
    }
    
    // Write (For files only)
    public void writeFile(String path, String content) {
        try {
            if (path == null || path.isEmpty()) {
                logger.warning("E: Path cannot be null or empty.");
                return;
            }

            path = path.replace("/+", "/");

            String sql = "UPDATE fs_files SET content = ? WHERE path = ?";
            long rowsAffected = ((Number) DB.query(sql, content, path).getFirst().getFirst()).longValue();
            if (rowsAffected == 0) {
                logger.warning("E: File not found or could not be updated: " + path);
            }
        } catch (Exception e) {
            logger.warning("E: An error occurred while writing to the file: " + e.getMessage());
        }
    }

    public void appendFile(String path, String content) {
        try {
            if (path == null || path.isEmpty()) {
                logger.warning("E: Path cannot be null or empty.");
                return;
            }

            path = path.replace("/+", "/");

            String sql = "UPDATE fs_files SET content = CONCAT(content, ?) WHERE path = ?";
            long rowsAffected = ((Number) DB.query(sql, content, path).getFirst().getFirst()).longValue();
            if (rowsAffected == 0) {
                logger.warning("E: File not found or could not be updated: " + path);
            }
        } catch (Exception e) {
            logger.warning("E: An error occurred while appending to the file: " + e.getMessage());
        }
    }

    // Permissions
    public boolean hasPermissions(String perms, String requiredPerm) {
        if (plr == null) {
            return true;
        }
        char permChar = perms.charAt(0);
        return !switch (requiredPerm) {
            case "r" -> (permChar - '0' & 4) != 0;
            case "w" -> (permChar - '0' & 2) != 0;
            case "x" -> (permChar - '0' & 1) != 0;
            default -> false;
        };
    }

    public void changePermissions(String path, String newPerms) {
        try {
            if (path == null || path.isEmpty() || newPerms == null || newPerms.length() != 3) {
                logger.warning("E: Path cannot be null or empty, or new permissions cannot be null or invalid.");
                return;
            }
            path = path.replace("/+", "/");

            String symbolicPerms = ConvertPerms.octalToSymbolic(newPerms);

            String dirSql = "SELECT COUNT(*) FROM fs_dirs WHERE path = ?";
            long dirCount = ((Number) DB.query(dirSql, path).getFirst().getFirst()).longValue();

            String fileSql = "SELECT COUNT(*) FROM fs_files WHERE path = ?";
            long fileCount = ((Number) DB.query(fileSql, path).getFirst().getFirst()).longValue();

            if (dirCount > 0) {
                String updateDirSql = "UPDATE fs_dirs SET permissions = ? WHERE path = ?";
                DB.query(updateDirSql, newPerms, path);
                logger.info("I: Directory permissions changed for " + path + " to " + newPerms + " (" + symbolicPerms + ")");
            } else if (fileCount > 0) {
                String updateFileSql = "UPDATE fs_files SET permissions = ? WHERE path = ?";
                DB.query(updateFileSql, newPerms, path);
                logger.info("I: File permissions changed for " + path + " to " + newPerms + " (" + symbolicPerms + ")");
            } else {
                logger.warning("E: Path not found: " + path);
            }
        } catch (Exception e) {
            logger.warning("E: An error occurred while changing permissions: " + e.getMessage());
        }
    }
}
