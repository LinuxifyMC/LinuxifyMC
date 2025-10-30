// Remade FakeFS
package com.opuadm.linuxifymc.machine.fs;

import com.opuadm.linuxifymc.Database;
import com.opuadm.linuxifymc.machine.shell.SudoContext;

import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeFS {
    // Variables
    public static String FS_VER = "0.1.1";

    public static Database DB;

    private final long maxDiskSpace = 768L * ConvertUnits.GB; // 768GB Disk Space Available
    @SuppressWarnings("FieldMayBeFinal")
    private volatile long diskSpaceUsed = 1722 * ConvertUnits.MB; // "1.722GB" Disk Space Used (As default with only system files)
    private volatile long diskSpaceUsedByUserFiles = 0L; // 0 Bytes by default
    private volatile long totalDiskSpaceUsed = diskSpaceUsed + diskSpaceUsedByUserFiles;
    private volatile long diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed; // Free Disk Space

    private static final String defaultGroup = "users";
    private static final Logger logger = Logger.getLogger(FakeFS.class.getName());

    private static final ConcurrentHashMap<UUID, FakeFS> PLAYER_FS = new ConcurrentHashMap<>();

    @SuppressWarnings("FieldMayBeFinal")
    public String CurDir;

    private String plr;
    private UUID playerUuid;

    // Main Public for FakeFS
    public FakeFS(String playerName) {
        this.plr = playerName;
        this.playerUuid = null;
        this.CurDir = "/home/" + this.plr.toLowerCase();
        initializeFSDatabase();
    }

    // Main Private for FakeFS
    private FakeFS(UUID uuid, String playerName) {
        this.plr = playerName;
        this.playerUuid = uuid;
        this.CurDir = "/home/" + this.plr.toLowerCase();
        initializeFSDatabase();
        loadOrCreateSaveRow();
    }

    // DB Init
    private void initializeFSDatabase() {
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return;
        }

        DB.query("CREATE TABLE IF NOT EXISTS fs_dirs (" +
                "player_uuid TEXT NOT NULL, " +
                "path TEXT NOT NULL, " +
                "owner TEXT NOT NULL, " +
                "group_name TEXT NOT NULL, " +
                "permissions TEXT NOT NULL, " +
                "PRIMARY KEY (player_uuid, path))");

        DB.query("CREATE TABLE IF NOT EXISTS fs_files (" +
                "player_uuid TEXT NOT NULL, " +
                "path TEXT NOT NULL, " +
                "owner TEXT NOT NULL, " +
                "group_name TEXT NOT NULL, " +
                "permissions TEXT NOT NULL, " +
                "content TEXT, " +
                "PRIMARY KEY (player_uuid, path))");

        DB.query("CREATE TABLE IF NOT EXISTS fs_saves (" +
                "player_uuid TEXT PRIMARY KEY, " +
                "player_name TEXT NOT NULL, " +
                "fs_version TEXT NOT NULL, " +
                "disk_space_used INTEGER, " +
                "disk_space_free INTEGER, " +
                "current_dir TEXT)");
    }

    // Generic / Misc
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
            String uuid = fsInstance.playerUuid != null ? fsInstance.playerUuid.toString() : null;
            if (uuid == null) {
                logger.warning("E: upgradeFS requires player UUID.");
                return;
            }

            var selectRes = DB.query("SELECT fs_version FROM fs_saves WHERE player_uuid = ?", uuid);
            if (selectRes == null || selectRes.isEmpty()) {
                DB.query("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                        uuid, fsInstance.plr, FS_VER,
                        fsInstance.diskSpaceUsed + fsInstance.diskSpaceUsedByUserFiles,
                        fsInstance.maxDiskSpace - (fsInstance.diskSpaceUsed + fsInstance.diskSpaceUsedByUserFiles),
                        fsInstance.CurDir);
                logger.info("I: Filesystem record for " + fsInstance.plr + " created with version " + FS_VER);
                return;
            }

            Object stored = selectRes.getFirst().getFirst();
            if (stored == null) {
                logger.warning("E: Stored fs_version is null for " + fsInstance.plr);
                return;
            }
            String storedVersion = stored.toString();

            if (storedVersion.equals(FS_VER)) {
                logger.info("I: Filesystem for " + fsInstance.plr + " is already up-to-date (" + FS_VER + ")");
                return;
            }

            DB.query("UPDATE fs_saves SET fs_version = ? WHERE player_uuid = ?",
                    FS_VER, uuid);

            logger.info("I: Filesystem for " + fsInstance.plr + " upgraded from " + storedVersion + " to " + FS_VER);
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while upgrading the filesystem: " + e.getMessage(), e);
        }
    }

    private void txWriteFile(String path, String newContent, boolean append) {
        if (playerUuid == null) return;
        try {
            DB.runInTransaction(conn -> {
                Object lenObj = DB.singleValueQuery("SELECT length(content) FROM fs_files WHERE player_uuid = ? AND path = ?",
                        playerUuid.toString(), path);
                if (lenObj == null && !append) {
                    DB.executeUpdate("INSERT INTO fs_files (player_uuid, path, owner, group_name, permissions, content) VALUES (?, ?, ?, ?, ?, ?)",
                            playerUuid.toString(), path, plr, defaultGroup, "644", "");
                    lenObj = 0;
                }
                if (lenObj == null) {
                    logger.warning("E: File not found or could not be updated: " + path);
                    return null;
                }

                long oldLen = (lenObj instanceof Number n) ? n.longValue() : 0L;
                long newLen;

                if (append) {
                    DB.executeUpdate("UPDATE fs_files SET content = COALESCE(content, '') || ? WHERE player_uuid = ? AND path = ?",
                            newContent != null ? newContent : "", playerUuid.toString(), path);
                    newLen = oldLen + (newContent != null ? newContent.length() : 0);
                } else {
                    DB.executeUpdate("UPDATE fs_files SET content = ? WHERE player_uuid = ? AND path = ?",
                            newContent, playerUuid.toString(), path);
                    newLen = (newContent != null ? newContent.length() : 0);
                }

                long delta = newLen - oldLen;
                adjustUserFileBytes(delta);
                updateDiskSpaceUsage();

                DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                        playerUuid.toString(), plr, FS_VER, totalDiskSpaceUsed, diskSpaceFree, CurDir);
                return null;
            });
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: txWriteFile failed: " + e.getMessage(), e);
        }
    }

    public boolean setCurrentDir(String target) {
        if (playerUuid == null) return false;
        if (target == null || target.isEmpty()) return false;
        Deque<String> stack = getStrings(target);
        String finalPath = "/" + String.join("/", stack);
        if (finalPath.length() > 1 && finalPath.endsWith("/")) finalPath = finalPath.substring(0, finalPath.length() - 1);

        String verified = getDir(finalPath);
        if (verified == null) {
            logger.fine("E: setCurrentDir failed: target=" + target + " resolved=" + finalPath);
            return false;
        }

        try {
            this.CurDir = verified;
            DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                    playerUuid.toString(), plr, FS_VER, totalDiskSpaceUsed, diskSpaceFree, this.CurDir);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: setCurrentDir persist failed: " + e.getMessage(), e);
            return false;
        }
    }

    private @NotNull Deque<String> getStrings(String target) {
        String home = "/home/" + plr.toLowerCase();
        String norm = target.trim();
        if (norm.equals("~")) {
            norm = home;
        } else if (norm.startsWith("~/")) {
            norm = home + norm.substring(1);
        }

        if (!norm.startsWith("/")) {
            String base = (this.CurDir == null || this.CurDir.isEmpty()) ? "/" : this.CurDir;
            norm = (base.equals("/") ? "" : base) + "/" + norm;
        }

        String[] parts = norm.replaceAll("/+", "/").split("/");
        Deque<String> stack = new java.util.ArrayDeque<>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!stack.isEmpty()) stack.removeLast();
                continue;
            }
            stack.addLast(p);
        }
        return stack;
    }

    // Save/Load
    public void loadFS(UUID playerUuid) {
        if (playerUuid == null) return;
        this.playerUuid = playerUuid;

        FakeFS cached = PLAYER_FS.get(playerUuid);
        if (cached != null && cached.plr != null && !cached.plr.isEmpty()) {
            this.plr = cached.plr;
        } else {
            try {
                var res = DB.query("SELECT player_name FROM fs_saves WHERE player_uuid = ?", playerUuid.toString());
                if (res != null && !res.isEmpty()) {
                    Object nameObj = res.getFirst().getFirst();
                    if (nameObj instanceof String s && !s.isEmpty()) {
                        this.plr = s;
                    }
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "I: could not read stored player name for " + playerUuid + ": " + e.getMessage());
            }
        }

        if (this.plr == null || this.plr.isEmpty()) {
            this.plr = "player-" + playerUuid.toString().substring(0, 8);
        }

        if (this.CurDir == null || this.CurDir.isEmpty()) {
            this.CurDir = "/home/" + this.plr.toLowerCase();
        }

        try {
            loadOrCreateSaveRow();
            Object rootCnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), "/");
            Object homeCnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), "/home");
            long r = rootCnt instanceof Number ? ((Number) rootCnt).longValue() : 0L;
            long h = homeCnt instanceof Number ? ((Number) homeCnt).longValue() : 0L;
            if (r == 0L || h == 0L) {
                logger.info("I: system dirs missing for " + this.plr + ", creating them.");
                setupSysFiles();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: loadFS failed: " + e.getMessage(), e);
        }
    }


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
            String sql = "INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            long totalUsed = fsInstance.diskSpaceUsed + fsInstance.getUserFileBytes();
            long free = fsInstance.maxDiskSpace - totalUsed;
            DB.query(sql, player.getUniqueId().toString(), player.getName(), FS_VER, totalUsed, free, fsInstance.CurDir);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while saving the filesystem: " + e.getMessage(), e);
            return false;
        }
    }

    // Setup
    public synchronized void setupSysFiles() {
        if (playerUuid == null) {
            logger.warning("E: setupSysFiles requires player UUID.");
            return;
        }

        try {
            Object rootExists = DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), "/");
            long rootCnt = rootExists instanceof Number ? ((Number) rootExists).longValue() : 0L;
            if (rootCnt == 0L) {
                DB.executeUpdate("INSERT INTO fs_dirs (player_uuid, path, owner, group_name, permissions) VALUES (?, ?, ?, ?, ?)",
                        playerUuid.toString(), "/", "root", defaultGroup, "755");
                logger.info("I: created root directory for " + plr);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: Could not ensure root directory: " + e.getMessage(), e);
        }

        try {
            String[] sysDirs = new String[] {
                    "/sys", "/dev", "/etc", "/boot", "/proc", "/root",
                    "/usr", "/home", "/tmp", "/var", "/opt",
                    "/usr/bin", "/usr/sbin", "/usr/lib", "/usr/lib64", "/usr/local",
                    "/var/log"
            };
            for (String d : sysDirs) {
                Object cnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                        playerUuid.toString(), d);
                long c = cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
                if (c == 0L) {
                    DB.executeUpdate("INSERT INTO fs_dirs (player_uuid, path, owner, group_name, permissions) VALUES (?, ?, ?, ?, ?)",
                            playerUuid.toString(), d, "root", defaultGroup, "755");
                    logger.fine("F: setupSysFiles inserted: " + d);
                }
            }

            String homePath = "/home/" + this.plr.toLowerCase();
            Object homeCntObj = DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), homePath);
            long homeCntVal = homeCntObj instanceof Number ? ((Number) homeCntObj).longValue() : 0L;
            if (homeCntVal == 0L) {
                DB.executeUpdate("INSERT INTO fs_dirs (player_uuid, path, owner, group_name, permissions) VALUES (?, ?, ?, ?, ?)",
                        playerUuid.toString(), homePath, this.plr.toLowerCase(), defaultGroup, "755");
                logger.fine("F: setupSysFiles inserted player home: " + homePath);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: setupSysFiles direct inserts failed: " + e.getMessage(), e);
        }
    }

    // Player Filesystem (per-player by UUID)
    public static FakeFS getPlayerFS(UUID plrUUID, String username) {
        Objects.requireNonNull(plrUUID, "E: UUID cannot be null.");
        Objects.requireNonNull(username, "E: Username cannot be null.");
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return null;
        }
        return PLAYER_FS.compute(plrUUID, (id, existing) -> {
            if (existing != null && existing.plr.equalsIgnoreCase(username)) return existing;
            return new FakeFS(id, username);
        });
    }

    // Make (Directories, Files, etc.)
    public synchronized void makeDir(String path, String owner, String perms) {
        if (playerUuid == null) {
            logger.warning("W: makeDir called on uninitialized FakeFS (playerUuid == null). Attempting to recover using player name: " + this.plr);
            return;
        }
        try {
            if (path != null && !path.startsWith("/")) { String base = (this.CurDir == null || this.CurDir.isEmpty()) ? "/" : this.CurDir; path = (base.equals("/") ? "" : base) + "/" + path; }
            path = path == null ? null : path.replaceAll("/+", "/");
            path = getString(path);
            if (path == null) return;

            if (owner == null || owner.isEmpty()) {
                owner = this.plr != null ? this.plr.toLowerCase() : "root";
            }
            if (perms == null || perms.length() != 3) {
                perms = "755";
            }

            Object cntObj = DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            long dirCount = cntObj instanceof Number ? ((Number) cntObj).longValue() : 0L;
            if (dirCount > 0) return;

            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            if (parent.isEmpty()) parent = "/";
            var pr = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                logger.fine("E: makeDir: parent not found: " + parent);
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.get(0);
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (lacksPermissions(parentPerms, parentOwner, parentGroup, this.plr, "w") && !SudoContext.isSudo()) {
                logger.fine("E: makeDir: permission denied for player=" + this.plr + " parent=" + parent + " perms=" + parentPerms);
                return;
            }

            DB.executeUpdate("INSERT INTO fs_dirs (player_uuid, path, owner, group_name, permissions) VALUES (?, ?, ?, ?, ?)",
                    playerUuid.toString(), path, owner, defaultGroup, perms);

            logger.info("I: makeDir inserted: " + path + " owner=" + owner + " perms=" + perms);

            DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                    playerUuid.toString(), plr, FS_VER, totalDiskSpaceUsed, diskSpaceFree, CurDir);

            changePermissions(path, perms);
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while creating directory: " + e.getMessage(), e);
        }
    }

    public synchronized void makeFile(String path, String owner, String perms, String content) {
        if (playerUuid == null) {
            logger.warning("W: makeFile called on uninitialized FakeFS (playerUuid == null). Player name: " + this.plr);
            return;
        }
        try {
            if (path != null && !path.startsWith("/")) {
                String base = (this.CurDir == null || this.CurDir.isEmpty()) ? "/" : this.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = path == null ? null : path.replaceAll("/+", "/");

            path = getString(path);
            if (path == null) {
                return;
            }

            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
            if (parent.isEmpty()) parent = "/";
            var pr = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                logger.fine("E: makeFile: parent not found: " + parent);
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.get(0);
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (lacksPermissions(parentPerms, parentOwner, parentGroup, this.plr, "w") && SudoContext.isSudo()) {
                logger.fine("E: makeFile: permission denied for player=" + this.plr + " parent=" + parent + " perms=" + parentPerms);
                return;
            }

            if (owner == null || owner.isEmpty()) {
                owner = this.plr != null ? this.plr.toLowerCase() : "root";
            }
            if (perms == null || perms.length() != 3) {
                perms = "644";
            }

            if (content != null && content.length() > diskSpaceFree) return;

            Object countObj = DB.singleValueQuery("SELECT COUNT(*) AS cnt FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            if (countObj == null) return;
            long fileCount = (countObj instanceof Number) ? ((Number) countObj).longValue() : 0L;
            if (fileCount > 0) return;

            DB.executeUpdate("INSERT INTO fs_files (player_uuid, path, owner, group_name, permissions, content) VALUES (?, ?, ?, ?, ?, ?)",
                    playerUuid.toString(), path, owner, defaultGroup, perms, content);

            logger.info("I: makeFile inserted: " + path + " owner=" + owner + " perms=" + perms + " size=" + (content==null?0:content.length()));

            changePermissions(path, perms);
            if (content != null) {
                adjustUserFileBytes(content.length());
                updateDiskSpaceUsage();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while creating file: " + e.getMessage(), e);
        }
    }

    // Remove (Directories, Files, etc.)
    public synchronized void deleteFile(String path) {
        if (playerUuid == null) return;
        if (path == null || path.isEmpty()) {
            logger.warning("E: deleteFile requires a valid path.");
            return;
        }
        try {
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
            if (parent.isEmpty()) parent = "/";
            var pr = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                logger.fine("E: deleteFile: parent not found: " + parent);
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.get(0);
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (lacksPermissions(parentPerms, parentOwner, parentGroup, this.plr, "w") && !SudoContext.isSudo()) {
                logger.fine("E: deleteFile: permission denied for player=" + this.plr + " parent=" + parent + " perms=" + parentPerms);
                return;
            }
            Object lenObj = DB.singleValueQuery("SELECT length(content) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            long oldLen = (lenObj instanceof Number n) ? n.longValue() : 0L;

            int deleted = DB.executeUpdate("DELETE FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            if (deleted > 0) {
                // adjust disk usage
                adjustUserFileBytes(-oldLen);
                updateDiskSpaceUsage();
                DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                        playerUuid.toString(), plr, FS_VER, totalDiskSpaceUsed, diskSpaceFree, CurDir);
                logger.info("I: File deleted: " + path);
            } else {
                logger.fine("I: deleteFile: file not found: " + path);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: deleteFile failed: " + e.getMessage(), e);
        }
    }

    public synchronized void deleteDir(String path, boolean recursive, boolean force) {
        if (playerUuid == null) return;
        if (path == null || path.isEmpty()) {
            logger.warning("E: deleteDir requires a valid path.");
            return;
        }
        try {
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            var dirCheck = DB.query("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            long dirCount = 0;
            if (dirCheck != null && !dirCheck.isEmpty()) {
                dirCount = ((Number) dirCheck.getFirst().getFirst()).longValue();
            }
            if (dirCount == 0) {
                logger.warning("E: deleteDir: Directory not found: " + path);
                return;
            }

            String like = path.equals("/") ? "/%" : path + "/%";

            var contentRows = DB.query(
                    "SELECT (SELECT COUNT(*) FROM fs_dirs d WHERE d.player_uuid = ? AND d.path LIKE ?) AS dircnt, " +
                            "(SELECT COALESCE(sum(length(content)),0) FROM fs_files f WHERE f.player_uuid = ? AND f.path LIKE ?) AS filesize, " +
                            "(SELECT COUNT(*) FROM fs_files f WHERE f.player_uuid = ? AND f.path LIKE ?) AS filecnt",
                    playerUuid.toString(), like,
                    playerUuid.toString(), like,
                    playerUuid.toString(), like
            );

            long dirCntInside = 0;
            long totalFilesSize = 0;
            long fileCnt = 0;
            if (contentRows != null && !contentRows.isEmpty()) {
                var row = contentRows.getFirst();
                dirCntInside = ((Number) row.get(0)).longValue();
                Object sizeObj = row.get(1);
                totalFilesSize = (sizeObj instanceof Number n) ? n.longValue() : 0L;
                fileCnt = ((Number) row.get(2)).longValue();
            }

            if (!recursive && (dirCntInside > 0 || fileCnt > 0)) {
                logger.warning("E: deleteDir refused: directory not empty: " + path);
                return;
            }

            if (recursive) {
                String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
                if (parent.isEmpty()) parent = "/";
                var pr = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                        playerUuid.toString(), parent);
                if (pr == null || pr.isEmpty()) {
                    logger.fine("E: deleteDir: parent not found: " + parent);
                    return;
                }
                List<Object> prow = pr.getFirst();
                String parentOwner = (String) prow.get(0);
                String parentGroup = (String) prow.get(1);
                String parentPerms = (String) prow.get(2);
                if (lacksPermissions(parentPerms, parentOwner, parentGroup, this.plr, "w") && !SudoContext.isSudo()) {
                    logger.fine("E: deleteDir: permission denied for player=" + this.plr + " parent=" + parent + " perms=" + parentPerms);
                    return;
                }
                if (totalFilesSize > 0) {
                    adjustUserFileBytes(-totalFilesSize);
                }

                DB.executeUpdate("DELETE FROM fs_files WHERE player_uuid = ? AND path LIKE ?",
                        playerUuid.toString(), like);
                DB.executeUpdate("DELETE FROM fs_dirs WHERE player_uuid = ? AND path LIKE ?",
                        playerUuid.toString(), like);
            }

            DB.executeUpdate("DELETE FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);

            updateDiskSpaceUsage();
            DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                    playerUuid.toString(), plr, FS_VER, totalDiskSpaceUsed, diskSpaceFree, CurDir);

            logger.info("I: Directory deleted: " + path + (force ? " (forced)" : ""));
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: deleteDir failed: " + e.getMessage(), e);
        }
    }

    // Get (Directories, Files, etc.)
    public String getDir(String path) {
        if (playerUuid == null) return null;
        try {
            if (path == null || path.isEmpty()) {
                return null;
            }
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            var result = DB.query("SELECT path FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            if (result == null || result.isEmpty()) {
                logger.fine("E: Directory not found for player=" + plr + ": " + path);
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while retrieving the directory: " + e.getMessage(), e);
            return null;
        }
        return path;
    }

    public String getFile(String path) {
        if (playerUuid == null) return null;
        try {
            if (path == null || path.isEmpty()) return null;
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            var result = DB.query("SELECT content FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            if (result == null || result.isEmpty()) return null;

            Object content = result.getFirst().getFirst();
            return content != null ? content.toString() : null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while reading file: " + e.getMessage(), e);
            return null;
        }
    }

    public String listCurrentDir(String path, boolean showHidden, boolean showDetails) {
        if (playerUuid == null) return "";
        String d = (path == null || path.isEmpty()) ? CurDir : path;
        d = d == null || d.isEmpty() ? "/" : d.replaceAll("/+", "/");
        if (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        String like = d.equals("/") ? "/%" : d + "/%";
        String notLike = d.equals("/") ? "/%/%" : d + "/%/%";
        var rows = DB.query(
                "SELECT 'D' as t, path, owner, permissions FROM fs_dirs WHERE player_uuid=? AND path LIKE ? AND path NOT LIKE ? " +
                        "UNION ALL SELECT 'F', path, owner, permissions FROM fs_files WHERE player_uuid=? AND path LIKE ? AND path NOT LIKE ?",
                playerUuid.toString(), like, notLike, playerUuid.toString(), like, notLike);
        if (rows == null) return "";
        StringBuilder out = new StringBuilder();
        for (var r : rows) {
            String p = r.get(1).toString();
            String name = p.substring(p.lastIndexOf('/') + 1);
            if (!showHidden && name.startsWith(".")) continue;
            if (showDetails) {
                out.append(String.format("%s %s owner=%s perms=%s", r.get(0), p, r.get(2), r.get(3)));
            } else {
                out.append(name);
                if (r.getFirst().equals("D")) out.append("/");
            }
            out.append('\n');
        }
        return out.toString();
    }

    // Write (For files only)
    public synchronized void writeFile(String path, String content) {
        if (playerUuid == null) return;
        try {
            if (path == null || path.isEmpty()) {
                logger.warning("E: Path cannot be null or empty.");
                return;
            }
            path = path.replaceAll("/+", "/");
            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            if (parent.isEmpty()) parent = "/";
            var pr = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                logger.fine("E: writeFile: parent not found: " + parent);
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.get(0);
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (lacksPermissions(parentPerms, parentOwner, parentGroup, this.plr, "w") && !com.opuadm.linuxifymc.machine.shell.SudoContext.isSudo()) {
                logger.fine("E: writeFile: permission denied for player=" + this.plr + " parent=" + parent + " perms=" + parentPerms);
                return;
            }
            txWriteFile(path, content, false);
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while writing to the file: " + e.getMessage(), e);
        }
    }

    public synchronized void appendFile(String path, String content) {
        if (playerUuid == null) return;
        try {
            if (path == null || path.isEmpty()) {
                logger.warning("E: Path cannot be null or empty.");
                return;
            }
            path = path.replaceAll("/+", "/");

            Object existsCnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            long cnt = (existsCnt instanceof Number n) ? n.longValue() : 0L;
            if (cnt == 0L) {
                logger.warning("E: File not found or could not be updated: " + path);
                return;
            }
            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
            if (parent.isEmpty()) parent = "/";
            var pr = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                logger.fine("E: appendFile: parent not found: " + parent);
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.get(0);
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (lacksPermissions(parentPerms, parentOwner, parentGroup, this.plr, "w") && !com.opuadm.linuxifymc.machine.shell.SudoContext.isSudo()) {
                logger.fine("E: appendFile: permission denied for player=" + this.plr + " parent=" + parent + " perms=" + parentPerms);
                return;
            }
            txWriteFile(path, content, true);
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while appending to the file: " + e.getMessage(), e);
        }
    }

    // Permissions / Ownership
    public boolean hasPermissions(String perms, String owner, String group, String subject, String requiredPerm) {
        if (perms == null || perms.length() != 3) return false;

        int idx;
        if (subject != null && subject.equalsIgnoreCase(owner)) {
            idx = 0;
        } else if (subject != null && subject.equalsIgnoreCase(group)) {
            idx = 1;
        } else {
            idx = 2;
        }

        int digit = perms.charAt(idx) - '0';
        return switch (requiredPerm) {
            case "r" -> (digit & 4) != 0;
            case "w" -> (digit & 2) != 0;
            case "x" -> (digit & 1) != 0;
            default -> false;
        };
    }

    public boolean lacksPermissions(String perms, String owner, String group, String subject, String requiredPerm) {
        return !hasPermissions(perms, owner, group, subject, requiredPerm);
    }

    public void changePermissions(String path, String newPerms) {
        if (playerUuid == null) return;
        try {
            if (path == null || path.isEmpty() || newPerms == null || newPerms.length() != 3) {
                logger.warning("E: Path cannot be null or empty, or new permissions cannot be null or invalid.");
                return;
            }
            path = path.replaceAll("/+", "/");

            String symbolicPerms = ConvertPerms.octalToSymbolic(newPerms);

            var dirRes = DB.query("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            long dirCount = 0;
            if (dirRes != null && !dirRes.isEmpty()) {
                dirCount = ((Number) dirRes.getFirst().getFirst()).longValue();
            }

            var fileRes = DB.query("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            long fileCount = 0;
            if (fileRes != null && !fileRes.isEmpty()) {
                fileCount = ((Number) fileRes.getFirst().getFirst()).longValue();
            }

            String curOwner = null;
            String curGroup = null;
            String curPerms = null;
            var metaRes = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ? " +
                            "UNION ALL SELECT owner, group_name, permissions FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path, playerUuid.toString(), path);
            if (metaRes != null && !metaRes.isEmpty()) {
                var meta = metaRes.getFirst();
                curOwner = meta.get(0) != null ? meta.get(0).toString() : null;
                curGroup = meta.get(1) != null ? meta.get(1).toString() : null;
                curPerms = meta.get(2) != null ? meta.get(2).toString() : null;
            }

            if ((curPerms == null || lacksPermissions(curPerms, curOwner, curGroup, this.plr, "w"))
                    && !com.opuadm.linuxifymc.machine.shell.SudoContext.isSudo()) {
                logger.fine("E: changePermissions: permission denied for player=" + this.plr + " path=" + path);
                return;
            }

            if (dirCount > 0) {
                DB.query("UPDATE fs_dirs SET permissions = ? WHERE player_uuid = ? AND path = ?",
                        newPerms, playerUuid.toString(), path);
                logger.info("I: Directory permissions changed for " + path + " to " + newPerms + " (" + symbolicPerms + ")");
            } else if (fileCount > 0) {
                DB.query("UPDATE fs_files SET permissions = ? WHERE player_uuid = ? AND path = ?",
                        newPerms, playerUuid.toString(), path);
                logger.info("I: File permissions changed for " + path + " to " + newPerms + " (" + symbolicPerms + ")");
            } else {
                logger.warning("E: Path not found: " + path);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while changing permissions: " + e.getMessage(), e);
        }
    }

    public synchronized void changeOwner(String path, String newOwner) {
        if (playerUuid == null) {
            logger.warning("E: changeOwner requires player UUID.");
            return;
        }
        if (path == null || path.isEmpty() || newOwner == null || newOwner.isEmpty()) {
            logger.warning("E: changeOwner requires a valid path and owner.");
            return;
        }
        try {
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            var dirRes = DB.query("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            long dirCount = 0;
            if (dirRes != null && !dirRes.isEmpty()) {
                dirCount = ((Number) dirRes.getFirst().getFirst()).longValue();
            }

            var fileRes = DB.query("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path);
            long fileCount = 0;
            if (fileRes != null && !fileRes.isEmpty()) {
                fileCount = ((Number) fileRes.getFirst().getFirst()).longValue();
            }

            var ownerRes = DB.query("SELECT owner FROM fs_dirs WHERE player_uuid = ? AND path = ? UNION ALL SELECT owner FROM fs_files WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), path, playerUuid.toString(), path);
            String curOwner = null;
            if (ownerRes != null && !ownerRes.isEmpty()) {
                curOwner = ownerRes.getFirst().getFirst().toString();
            }
            if ((curOwner == null || !curOwner.equalsIgnoreCase(this.plr)) && !com.opuadm.linuxifymc.machine.shell.SudoContext.isSudo()) {
                logger.fine("E: changeOwner: permission denied for player=" + this.plr + " path=" + path);
                return;
            }

            if (dirCount > 0) {
                DB.executeUpdate("UPDATE fs_dirs SET owner = ? WHERE player_uuid = ? AND path = ?",
                        newOwner, playerUuid.toString(), path);
                logger.info("I: Directory owner changed for " + path + " to " + newOwner);
            } else if (fileCount > 0) {
                DB.executeUpdate("UPDATE fs_files SET owner = ? WHERE player_uuid = ? AND path = ?",
                        newOwner, playerUuid.toString(), path);
                logger.info("I: File owner changed for " + path + " to " + newOwner);
            } else {
                logger.warning("E: changeOwner: Path not found: " + path);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: changeOwner failed: " + e.getMessage(), e);
        }
    }

    // Other / Uncategorized / Helpers
    @Nullable
    private String getString(String path) {
        if (playerUuid == null) {
            logger.warning("E: getString refused: playerUuid is null for player=" + this.plr + " path=" + path);
            return null;
        }
        if (path == null || path.isEmpty()) {
            logger.warning("E: getString refused: path null/empty for player=" + this.plr);
            return null;
        }

        if (!path.startsWith("/")) {
            String base = (this.CurDir == null || this.CurDir.isEmpty()) ? "/" : this.CurDir;
            path = (base.equals("/") ? "" : base) + "/" + path;
        }
        path = path.replaceAll("/+", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        String parentDir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        if (parentDir.isEmpty() && path.startsWith("/")) parentDir = "/";

        if (!parentDir.isEmpty()) {
            var parentResult = DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), parentDir);

            if (parentResult == null || parentResult.isEmpty()) {
                logger.fine("E: getString: parent directory not found for player=" + this.plr + " parent=" + parentDir + " path=" + path);
                return null;
            }

            List<Object> row = parentResult.getFirst();
            String parentOwner = (String) row.get(0);
            String parentGroup = (String) row.get(1);
            String parentPerms = (String) row.get(2);

            if (lacksPermissions(parentPerms, parentOwner, parentGroup, this.plr, "w")
                    && !com.opuadm.linuxifymc.machine.shell.SudoContext.isSudo()) {
                logger.fine("E: getString: write permission denied for player=" + this.plr + " on parent=" + parentDir + " perms=" + parentPerms + " owner=" + parentOwner + " group=" + parentGroup);
                return null;
            }
        }
        return path;
    }

    public String getCurrentDir() {
        return CurDir;
    }

    private synchronized void updateDiskSpaceUsage() {
        totalDiskSpaceUsed = diskSpaceUsed + getUserFileBytes();
        diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed;
    }

    private void loadOrCreateSaveRow() {
        if (playerUuid == null) return;
        try {
            var res = DB.query("SELECT fs_version, disk_space_used, disk_space_free, current_dir FROM fs_saves WHERE player_uuid = ?",
                    playerUuid.toString());
            if (res == null || res.isEmpty()) {
                DB.query("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                        playerUuid.toString(), plr, FS_VER, totalDiskSpaceUsed, diskSpaceFree, CurDir);
            } else {
                var row = res.getFirst();
                Object used = row.get(1);
                Object free = row.get(2);
                Object dir = row.get(3);
                if (used instanceof Number u) diskSpaceUsedByUserFiles = Math.max(0L, u.longValue() - diskSpaceUsed);
                updateDiskSpaceUsage();
                if (free instanceof Number f) {
                    logger.fine("I: stored disk_space_free = " + f.longValue());
                }
                if (dir instanceof String s && !s.isEmpty()) {
                    logger.fine("I: current_dir stored = " + s + " (CurDir field is final)");
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: loadOrCreateSaveRow failed: " + e.getMessage(), e);
        }
    }

    private synchronized void adjustUserFileBytes(long delta) {
        if (delta >= 0) diskSpaceUsedByUserFiles += delta;
        else diskSpaceUsedByUserFiles = Math.max(0L, diskSpaceUsedByUserFiles + delta);
    }

    private synchronized long getUserFileBytes() {
        return diskSpaceUsedByUserFiles;
    }
}