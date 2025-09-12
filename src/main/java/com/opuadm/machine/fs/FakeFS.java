// Remade FakeFS
package com.opuadm.machine.fs;

import com.opuadm.Database;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.Nullable;

public class FakeFS {
    // Variables
    public static String FS_VER = "0.1.1";

    public static Database DB;

    private final long maxDiskSpace = 768L * ConvertUnits.GB; // 768GB Disk Space Available
    private long diskSpaceUsed = 1722 * ConvertUnits.MB; // "1.722GB" Disk Space Used (As default with only system files)
    private long diskSpaceUsedByUserFiles = 0L; // 0 Bytes by default
    private long totalDiskSpaceUsed = diskSpaceUsed + diskSpaceUsedByUserFiles;
    private long diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed; // Free Disk Space

    private static final String defaultGroup = "users";
    private static final Logger logger = Logger.getLogger(FakeFS.class.getName());

    private static final ConcurrentHashMap<UUID, FakeFS> PLAYER_FS = new ConcurrentHashMap<>();

    private final String CurDir;

    private final String plr;

    // Main Public for FakeFS
    public FakeFS(String playerName) {
        this.plr = playerName;
        this.CurDir = "/home/" + this.plr.toLowerCase();
    }

    // Generic / Misc
    public boolean saveFS(Player player, FakeFS fsInstance) {
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return false;
        }
        if (fsInstance == null) {
            logger.warning("E: fsInstance is null.");
            return false;
        }
        try {
            String sql = "INSERT INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), fs_version = VALUES(fs_version), disk_space_used = VALUES(disk_space_used), disk_space_free = VALUES(disk_space_free), current_dir = VALUES(current_dir)";
            long totalUsed = fsInstance.diskSpaceUsed + fsInstance.diskSpaceUsedByUserFiles;
            long free = fsInstance.maxDiskSpace - totalUsed;
            DB.query(sql, player.getUniqueId().toString(), player.getName(), FS_VER, totalUsed, free, fsInstance.CurDir);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while saving the filesystem: " + e.getMessage(), e);
            return false;
        }
    }

    public void upgradeFS(FakeFS fsInstance) {
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return;
        }
        if (fsInstance == null) {
            logger.warning("E: fsInstance is null.");
            return;
        }
        try {
            String sql = "UPDATE fs_saves SET fs_version = ? WHERE player_name = ? AND fs_version < ?";
            DB.query(sql, FS_VER, fsInstance.plr, FS_VER);
            logger.info("I: Filesystem for " + fsInstance.plr + " upgraded to version " + FS_VER);
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while upgrading the filesystem: " + e.getMessage(), e);
        }
    }


    // Setup
    public synchronized void setupSysFiles() {
        // System Directories (From root directory)
        this.makeDir("/sys", "root", "755");
        this.makeDir("/dev", "root", "755");
        this.makeDir("/etc", "root", "755");
        this.makeDir("/boot", "root", "755");
        this.makeDir("/proc", "root", "755");
        this.makeDir("/root", "root", "755");
        this.makeDir("/usr", "root", "755");
        this.makeDir("/home", "root", "755");
        this.makeDir("/tmp", "root", "755");
        this.makeDir("/var", "root", "755");
        this.makeDir("/opt", "root", "755");
        // System Directories (From /usr directory)
        this.makeDir("/usr/bin", "root", "755");
        this.makeDir("/usr/sbin", "root", "755");
        this.makeDir("/usr/lib", "root", "755");
        this.makeDir("/usr/lib64", "root", "755");
        this.makeDir("/usr/local", "root", "755");
        // /var Directories
        this.makeDir("/var/log", "root", "755");
        // Player Home Directory
        this.makeDir("/home/" + this.plr.toLowerCase(), this.plr.toLowerCase(), "777");
    }


    // Player Filesystem
    public static FakeFS getPlayerFS(UUID plrUUID, String username) {
        Objects.requireNonNull(plrUUID, "E: UUID cannot be null.");
        Objects.requireNonNull(username, "E: Username cannot be null.");
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return null;
        }
        return PLAYER_FS.compute(plrUUID, (id, existing) ->
                existing == null || !existing.plr.equalsIgnoreCase(username) ? new FakeFS(username) : existing);
    }

    // Make (Directories, Files, etc.)
    public synchronized void makeDir(String path, String owner, String perms) {
        try {
            path = getString(path);
            if (path == null) return;

            var countResult = DB.query("SELECT COUNT(*) AS cnt FROM fs_dirs WHERE path = ?", path);
            if (countResult == null || countResult.isEmpty()) return;
            var countInfo = (java.util.Map<?,?>) countResult.getFirst();
            long dirCount = ((Number) countInfo.get("cnt")).longValue();

            if (dirCount > 0) return;

            DB.query("INSERT INTO fs_dirs (path, owner, group_name, permissions) VALUES (?, ?, ?, ?)", path, owner, defaultGroup, perms);

            changePermissions(path, perms);
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while creating directory: " + e.getMessage(), e);
        }
    }

    public synchronized void makeFile(String path, String owner, String perms, String content) {
        try {
            path = getString(path);
            if (path == null) return;

            if (content != null && content.length() > diskSpaceFree) return;

            var countResult = DB.query("SELECT COUNT(*) AS cnt FROM fs_files WHERE path = ?", path);
            if (countResult == null || countResult.isEmpty()) return;
            var countInfo = (java.util.Map<?,?>) countResult.getFirst();
            long fileCount = ((Number) countInfo.get("cnt")).longValue();
            if (fileCount > 0) return;

            DB.query("INSERT INTO fs_files (path, owner, group_name, permissions, content) VALUES (?, ?, ?, ?, ?)", path, owner, defaultGroup, perms, content);

            changePermissions(path, perms);
            if (content != null) {
                diskSpaceUsedByUserFiles += content.length();
                totalDiskSpaceUsed = diskSpaceUsed + diskSpaceUsedByUserFiles;
                diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while creating file: " + e.getMessage(), e);
        }
    }

    // Get (Directories, Files, etc.)
    public String getDir(String path) {
        try {
            if (path == null || path.isEmpty()) {
                return null;
            }
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String sql = "SELECT * FROM fs_dirs WHERE path = ?";

            var result = DB.query(sql, path);
            if (result == null || result.isEmpty()) {
                logger.warning("E: Directory not found: " + path);
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while retrieving the directory: " + e.getMessage(), e);
            return null;
        }
        return path;
    }

    public String getFile(String path) {
        try {
            if (path == null || path.isEmpty()) return null;
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String sql = "SELECT content FROM fs_files WHERE path = ?";
            var result = DB.query(sql, path);
            if (result == null || result.isEmpty()) return null;
            var row = (java.util.Map<?,?>) result.getFirst();
            Object content = row.get("content");
            return content != null ? content.toString() : null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while reading file: " + e.getMessage(), e);
            return null;
        }
    }

    // Write (For files only)
    public synchronized void writeFile(String path, String content) {
        try {
            if (path == null || path.isEmpty()) {
                logger.warning("E: Path cannot be null or empty.");
                return;
            }

            path = path.replaceAll("/+", "/");

            var exists = DB.query("SELECT COUNT(*) AS cnt FROM fs_files WHERE path = ?", path);
            if (exists == null || exists.isEmpty() || ((Number)((java.util.Map<?,?>) exists.getFirst()).get("cnt")).longValue() == 0L) {
                logger.warning("E: File not found or could not be updated: " + path);
            } else {
                DB.query("UPDATE fs_files SET content = ? WHERE path = ?", content, path);
                diskSpaceUsedByUserFiles += (content != null ? content.length() : 0);
                totalDiskSpaceUsed = diskSpaceUsed + diskSpaceUsedByUserFiles;
                diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while writing to the file: " + e.getMessage(), e);
        }
    }

    public synchronized void appendFile(String path, String content) {
        try {
            if (path == null || path.isEmpty()) {
                logger.warning("E: Path cannot be null or empty.");
                return;
            }

            path = path.replaceAll("/+", "/");

            var exists = DB.query("SELECT COUNT(*) AS cnt FROM fs_files WHERE path = ?", path);
            if (exists == null || exists.isEmpty() || ((Number)((java.util.Map<?,?>) exists.getFirst()).get("cnt")).longValue() == 0L) {
                logger.warning("E: File not found or could not be updated: " + path);
            } else {
                if (content != null) {
                    diskSpaceUsedByUserFiles += content.length();
                    totalDiskSpaceUsed = diskSpaceUsed + diskSpaceUsedByUserFiles;
                    diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed;
                }
                DB.query("UPDATE fs_files SET content = CONCAT(content, ?) WHERE path = ?", content, path);
                if (content != null) {
                    diskSpaceUsedByUserFiles += content.length();
                    totalDiskSpaceUsed = diskSpaceUsed + diskSpaceUsedByUserFiles;
                    diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed;
                }

            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while appending to the file: " + e.getMessage(), e);
        }
    }

    // Permissions
    public boolean hasPermissions(String perms, String owner, String group, String subject, String requiredPerm) {
        if (perms == null || perms.length() != 3) return false;
        int idx = 2;
        if (subject != null && subject.equalsIgnoreCase(owner)) {
            idx = 0;
        } else if (group != null && !group.isEmpty()) {
            idx = 1;
        }
        int digit = perms.charAt(idx) - '0';
        return switch (requiredPerm) {
            case "r" -> (digit & 4) != 0;
            case "w" -> (digit & 2) != 0;
            case "x" -> (digit & 1) != 0;
            default -> false;
            };
        }


    public void changePermissions(String path, String newPerms) {
        try {
            if (path == null || path.isEmpty() || newPerms == null || newPerms.length() != 3) {
                logger.warning("E: Path cannot be null or empty, or new permissions cannot be null or invalid.");
                return;
            }
            path = path.replaceAll("/+", "/");

            String symbolicPerms = ConvertPerms.octalToSymbolic(newPerms);

            String dirSql = "SELECT COUNT(*) FROM fs_dirs WHERE path = ?";
            long dirCount = 0;
            var dirRes = DB.query(dirSql, path);
            if (dirRes != null && !dirRes.isEmpty()) {
                var val = dirRes.getFirst().getFirst();
                if (val instanceof Number) dirCount = ((Number) val).longValue();
            }

            String fileSql = "SELECT COUNT(*) FROM fs_files WHERE path = ?";
            long fileCount = 0;
            var fileRes = DB.query(fileSql, path);
            if (fileRes != null && !fileRes.isEmpty()) {
                var val = fileRes.getFirst().getFirst();
                if (val instanceof Number) fileCount = ((Number) val).longValue();
            }

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
            logger.log(Level.WARNING, "E: An error occurred while changing permissions: " + e.getMessage(), e);
        }
    }

    // Other / Uncategorized
    @Nullable
    private String getString(String path) {
        if (path == null || path.isEmpty()) return null;

        path = path.replaceAll("/+", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        String parentDir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        if (parentDir.isEmpty() && path.startsWith("/")) parentDir = "/";

        if (!parentDir.isEmpty()) {
            var parentResult = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE path = ?", parentDir);

            if (parentResult == null || parentResult.isEmpty()) return null;

            var parentInfo = (java.util.Map<?,?>) parentResult.getFirst();
            String parentOwner = (String) parentInfo.get("owner");
            String parentGroup = (String) parentInfo.get("group_name");
            String parentPerms = (String) parentInfo.get("permissions");

            if (!hasPermissions(parentPerms, parentOwner, parentGroup, this.plr, "w")) return null;
        }
        return path;
    }

    public String getCurrentDir() {
        return CurDir;
    }
}