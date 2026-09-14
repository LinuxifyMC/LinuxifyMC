package com.opuadm.linuxifymc.machine.fs;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.Database;
import com.opuadm.linuxifymc.machine.bin.BinCompiler;
import com.opuadm.linuxifymc.machine.shell.ShellVars;
import com.opuadm.linuxifymc.machine.logs.CustomLogger;
import com.opuadm.linuxifymc.machine.logs.Levels;
import com.opuadm.linuxifymc.machine.clock.Timer;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.text.MessageFormat;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.Nullable;

public class FakeFS {
    public static String FS_VER = "0.1.2";

    public static Database DB;

    final long maxDiskSpace = 768L * ConvertUnits.GB;
    final long diskSpaceUsed = 1722 * ConvertUnits.MB;
    final AtomicLong diskSpaceUsedByUserFiles = new AtomicLong();
    volatile long totalDiskSpaceUsed = diskSpaceUsed + diskSpaceUsedByUserFiles.get();
    volatile long diskSpaceFree = maxDiskSpace - totalDiskSpaceUsed;

    static final String defaultGroup = "users";
    static final Logger logger = Logger.getLogger(FakeFS.class.getName());

    public String CurDir;

    String plr;
    UUID playerUuid;

    public FakeFS(String playerName) {
        this.plr = playerName;
        this.playerUuid = null;
        this.CurDir = "/home/" + this.plr.toLowerCase();
        initializeFSDatabase();
    }

    FakeFS(UUID uuid, String playerName) {
        this.plr = playerName;
        this.playerUuid = uuid;
        this.CurDir = "/home/" + this.plr.toLowerCase();
        initializeFSDatabase();
        loadOrCreateSaveRow();
    }

    private void initializeFSDatabase() {
        if (DB == null) {
            logger.warning("E: Database is not initialized.");
            return;
        }

        DB.executeUpdate("CREATE TABLE IF NOT EXISTS fs_dirs (" +
                "player_uuid TEXT NOT NULL, " +
                "path TEXT NOT NULL, " +
                "owner TEXT NOT NULL, " +
                "group_name TEXT NOT NULL, " +
                "permissions TEXT NOT NULL, " +
                "PRIMARY KEY (player_uuid, path))");

        DB.executeUpdate("CREATE TABLE IF NOT EXISTS fs_files (" +
                "player_uuid TEXT NOT NULL, " +
                "path TEXT NOT NULL, " +
                "owner TEXT NOT NULL, " +
                "group_name TEXT NOT NULL, " +
                "permissions TEXT NOT NULL, " +
                "content TEXT, " +
                "PRIMARY KEY (player_uuid, path))");

        DB.executeUpdate("CREATE TABLE IF NOT EXISTS fs_links (" +
                "player_uuid TEXT NOT NULL, " +
                "path TEXT NOT NULL, " +
                "target TEXT NOT NULL, " +
                "owner TEXT NOT NULL, " +
                "group_name TEXT NOT NULL, " +
                "permissions TEXT NOT NULL, " +
                "PRIMARY KEY (player_uuid, path))");

        DB.executeUpdate("CREATE TABLE IF NOT EXISTS fs_saves (" +
                "player_uuid TEXT PRIMARY KEY, " +
                "player_name TEXT NOT NULL, " +
                "fs_version TEXT NOT NULL, " +
                "disk_space_used INTEGER, " +
                "disk_space_free INTEGER, " +
                "current_dir TEXT)");
    }

    public void upgradeFS(FakeFS fsInstance) {
        if (DB == null) {
            if (LinuxifyMC.debugMode) {
                logger.warning("E: Database is not initialized.");
            }
            return;
        }
        if (fsInstance == null) {
            if (LinuxifyMC.debugMode) {
                logger.warning("E: fsInstance is null.");
            }
            return;
        }

        try {
            String uuid = fsInstance.playerUuid != null ? fsInstance.playerUuid.toString() : null;
            if (uuid == null) {
                if (LinuxifyMC.debugMode) {
                    logger.warning("E: upgradeFS requires player UUID.");
                }
                return;
            }

            fsInstance.initializeFSDatabase();

            var selectRes = DB.query("SELECT fs_version FROM fs_saves WHERE player_uuid = ?", uuid);
            if (selectRes == null || selectRes.isEmpty()) {
                DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                        uuid, fsInstance.plr, FS_VER,
                        fsInstance.diskSpaceUsed + FsDisk.userFileBytes(fsInstance),
                        fsInstance.maxDiskSpace - (fsInstance.diskSpaceUsed + FsDisk.userFileBytes(fsInstance)),
                        fsInstance.CurDir);
                logger.info("I: Filesystem record for " + fsInstance.plr + " created with version " + FS_VER);
                fsInstance.ensureBinaries();
                return;
            }

            Object stored = selectRes.getFirst().getFirst();
            if (stored == null) {
                if (LinuxifyMC.debugMode) {
                    logger.warning("E: Stored fs_version is null for " + fsInstance.plr);
                }
                return;
            }
            String storedVersion = stored.toString();

            if (storedVersion.equals(FS_VER)) {
                if (LinuxifyMC.debugMode) {
                    logger.info("I: Filesystem for " + fsInstance.plr + " is already up-to-date (" + FS_VER + ")");
                }
                CustomLogger.Log(getPlr(fsInstance), Levels.INFO, MessageFormat.format("[    {0}] Your filesystem is already up-to-date.", Timer.getStamp(fsInstance.playerUuid)));
                return;
            }

            DB.executeUpdate("UPDATE fs_saves SET fs_version = ? WHERE player_uuid = ?",
                    FS_VER, uuid);

            fsInstance.ensureBinaries();

            if (LinuxifyMC.debugMode) {
                logger.info("I: Filesystem for " + fsInstance.plr + " upgraded from " + storedVersion + " to " + FS_VER);
            }
            CustomLogger.Log(getPlr(fsInstance), Levels.INFO, MessageFormat.format("[    {0}] Your filesystem has been successfully upgraded from {1} to {2}!", Timer.getStamp(fsInstance.playerUuid), storedVersion, FS_VER));
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while upgrading the filesystem: " + e.getMessage(), e);
            CustomLogger.Log(getPlr(fsInstance), Levels.WARNING, MessageFormat.format("[    {0}] Error while upgrading the filesystem! Error: ${1} ${2}", Timer.getStamp(fsInstance.playerUuid), e.getMessage(), e));
        }
    }

    public boolean setCurrentDir(String target) {
        return FsPaths.setCurrentDir(this, target);
    }

    public void loadFS(UUID playerUuid) {
        if (playerUuid == null) return;
        if (this.playerUuid != null && !this.playerUuid.equals(playerUuid)) return;
        this.playerUuid = playerUuid;

        FakeFS cached = FsRegistry.PLAYER_FS.get(playerUuid);
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
                CustomLogger.Log(Bukkit.getPlayer(playerUuid), Levels.GENERAL, MessageFormat.format("[    {0}] Could not read stored player name for {1}. Error: ${2}", Timer.getStamp(playerUuid), playerUuid, e.getMessage()));
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
        refreshBinaries();
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
            long totalUsed = fsInstance.diskSpaceUsed + FsDisk.userFileBytes(fsInstance);
            long free = fsInstance.maxDiskSpace - totalUsed;
            DB.executeUpdate(sql, player.getUniqueId().toString(), player.getName(), FS_VER, totalUsed, free, fsInstance.CurDir);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: An error occurred while saving the filesystem: " + e.getMessage(), e);
            return false;
        }
    }

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
                    "/usr", "/home", "/tmp", "/var", "/opt", "/sbin",
                    "/usr/bin", "/usr/sbin", "/usr/lib", "/usr/local",
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

            Object binDirCnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), "/bin");
            long binDirs = binDirCnt instanceof Number ? ((Number) binDirCnt).longValue() : 0L;
            Object binLinkCnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_links WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), "/bin");
            long binLinks = binLinkCnt instanceof Number ? ((Number) binLinkCnt).longValue() : 0L;
            if (binDirs == 0L && binLinks == 0L) {
                DB.executeUpdate("INSERT INTO fs_links (player_uuid, path, target, owner, group_name, permissions) VALUES (?, ?, ?, ?, ?, ?)",
                        playerUuid.toString(), "/bin", "/usr/bin", "root", defaultGroup, "777");
                logger.fine("F: setupSysFiles inserted: /bin -> /usr/bin");
            }

            String homePath = "/home/" + this.plr.toLowerCase();
            Object homeCntObj = DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    playerUuid.toString(), homePath);
            long homeCntVal = homeCntObj instanceof Number ? ((Number) homeCntObj).longValue() : 0L;
            if (homeCntVal == 0L) {
                DB.executeUpdate("INSERT INTO fs_dirs (player_uuid, path, owner, group_name, permissions) VALUES (?, ?, ?, ?, ?)",
                        playerUuid.toString(), homePath, this.plr.toLowerCase(), defaultGroup, "755");
                if (LinuxifyMC.debugMode) {
                    logger.fine("F: setupSysFiles inserted player home: " + homePath);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: setupSysFiles direct inserts failed: " + e.getMessage(), e);
        }

        ensureBinaries();
    }

    private synchronized void ensureBinaries() {
        if (playerUuid == null) {
            return;
        }
        try {
            for (String cmd : ShellVars.cmds) {
                String className = BinCompiler.coreClass(cmd);
                if (className == null) {
                    continue;
                }
                byte[] core = BinCompiler.ownBytes(className);
                if (core == null) {
                    continue;
                }
                String row = FsNodes.BINARY_MAGIC + "|" + FsNodes.BINARY_VERSION + "|class=" + className + "\n" + Base64.getEncoder().encodeToString(core);
                Object binCnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                        playerUuid.toString(), "/usr/bin/" + cmd);
                long bin = binCnt instanceof Number ? ((Number) binCnt).longValue() : 0L;
                Object legacyCnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                        playerUuid.toString(), "/bin/" + cmd);
                long legacy = legacyCnt instanceof Number ? ((Number) legacyCnt).longValue() : 0L;
                Object linkCnt = DB.singleValueQuery("SELECT COUNT(*) FROM fs_links WHERE player_uuid = ? AND (path = ? OR path = ?)",
                        playerUuid.toString(), "/usr/bin/" + cmd, "/bin/" + cmd);
                long links = linkCnt instanceof Number ? ((Number) linkCnt).longValue() : 0L;
                if (bin == 0L && legacy == 0L && links == 0L) {
                    DB.executeUpdate("INSERT INTO fs_files (player_uuid, path, owner, group_name, permissions, content) VALUES (?, ?, ?, ?, ?, ?)",
                            playerUuid.toString(), "/usr/bin/" + cmd, "root", defaultGroup, "755", row);
                }
            }
            refreshBinaries();
            DB.executeUpdate("DELETE FROM fs_files WHERE player_uuid = ? AND (path = ? OR path = ?)",
                    playerUuid.toString(), "/usr/bin/test", "/bin/test");
            DB.executeUpdate("DELETE FROM fs_links WHERE player_uuid = ? AND (path = ? OR path = ?)",
                    playerUuid.toString(), "/usr/bin/test", "/bin/test");
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: ensureBinaries failed: " + e.getMessage(), e);
        }
    }

    private synchronized void refreshBinaries() {
        if (playerUuid == null) {
            return;
        }
        try {
            for (String cmd : ShellVars.cmds) {
                String className = BinCompiler.coreClass(cmd);
                if (className == null) {
                    continue;
                }
                byte[] core = BinCompiler.ownBytes(className);
                if (core == null) {
                    continue;
                }
                String row = FsNodes.BINARY_MAGIC + "|" + FsNodes.BINARY_VERSION + "|class=" + className + "\n" + Base64.getEncoder().encodeToString(core);
                Object current = DB.singleValueQuery("SELECT content FROM fs_files WHERE player_uuid = ? AND path = ?",
                        playerUuid.toString(), "/usr/bin/" + cmd);
                String existing = current != null ? current.toString() : "";
                if (!existing.equals(row) && isRefreshableCoreRow(existing)) {
                    DB.executeUpdate("UPDATE fs_files SET content = ? WHERE player_uuid = ? AND path = ?",
                            row, playerUuid.toString(), "/usr/bin/" + cmd);
                }
            }
            DB.executeUpdate("UPDATE fs_dirs SET permissions = ? WHERE player_uuid = ? AND path = ? AND owner = ? AND permissions = ?",
                    "777", playerUuid.toString(), "/tmp", "root", "755");
        } catch (Exception e) {
            logger.log(Level.WARNING, "E: refreshBinaries failed: " + e.getMessage(), e);
        }
    }

    private boolean isRefreshableCoreRow(String content) {
        if (content == null || content.isEmpty()) return true;
        if (content.equals(FsNodes.BINARY_MAGIC + "\nv=1\nexec=builtin\n")) return true;
        String[] header = BinCompiler.headerOf(content);
        String headerLine = header[0].trim();
        String prefix = FsNodes.BINARY_MAGIC + "|" + FsNodes.BINARY_VERSION;
        if (headerLine.startsWith(prefix + "|class=")) {
            String candidate = headerLine.substring(prefix.length() + 7);
            return BinCompiler.ownBytes(candidate) != null;
        }
        return false;
    }

    public static void registerChannels(Plugin plugin) {
        FsChannels.registerChannels(plugin);
    }

    public static void unregisterChannels(Plugin plugin) {
        FsChannels.unregisterChannels(plugin);
    }

    public synchronized void makeDir(String path, String owner, String perms) {
        FsNodes.makeDir(this, path, owner, perms);
    }

    public synchronized void makeFile(String path, String owner, String perms, String content) {
        FsNodes.makeFile(this, path, owner, perms, content);
    }

    public synchronized void deleteFile(String path) {
        FsNodes.deleteFile(this, path);
    }

    public synchronized void deleteDir(String path, boolean recursive, boolean force) {
        FsNodes.deleteDir(this, path, recursive, force);
    }

    public String getDir(String path) {
        return FsNodes.getDir(this, path);
    }

    public String getFile(String path) {
        return FsNodes.getFile(this, path);
    }

    public String listCurrentDir(String path, boolean showHidden, boolean showDetails) {
        return FsNodes.listCurrentDir(this, path, showHidden, showDetails);
    }

    public synchronized void writeFile(String path, String content) {
        FsNodes.writeFile(this, path, content);
    }

    public synchronized void appendFile(String path, String content) {
        FsNodes.appendFile(this, path, content);
    }

    public synchronized boolean makeLink(String linkPath, String target, String owner) {
        return FsNodes.makeLink(this, linkPath, target, owner);
    }

    public String readLink(String path) {
        return FsNodes.readLink(this, path);
    }

    public boolean canExecute(String path) {
        return FsNodes.canExecute(this, path);
    }

    public List<String> binaryNames() {
        return FsNodes.binaryNames(this);
    }

    public void changePermissions(String path, String newPerms) {
        FsMeta.changePermissions(this, path, newPerms);
    }

    public synchronized void changeOwner(String path, String newOwner) {
        FsMeta.changeOwner(this, path, newOwner);
    }

    public String getCurrentDir() {
        return CurDir;
    }

    private void loadOrCreateSaveRow() {
        if (playerUuid == null) return;
        try {
            var res = DB.query("SELECT fs_version, disk_space_used, disk_space_free, current_dir FROM fs_saves WHERE player_uuid = ?",
                    playerUuid.toString());
            if (res == null || res.isEmpty()) {
                DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                        playerUuid.toString(), plr, FS_VER, totalDiskSpaceUsed, diskSpaceFree, CurDir);
            } else {
                var row = res.getFirst();
                Object used = row.get(1);
                Object free = row.get(2);
                Object dir = row.get(3);
                if (used instanceof Number u) diskSpaceUsedByUserFiles.set(Math.max(0L, u.longValue() - diskSpaceUsed));
                FsDisk.updateDiskSpaceUsage(this);
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

    static @Nullable Player getPlr(FakeFS fsInstance) {
        Player targetPlr = null;
        if (fsInstance.playerUuid != null) {
            targetPlr = Bukkit.getPlayer(fsInstance.playerUuid);
        }
        return targetPlr;
    }
}
