package com.opuadm.linuxifymc.machine.fs;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.Database;
import com.opuadm.linuxifymc.machine.clock.Timer;
import com.opuadm.linuxifymc.machine.logs.CustomLogger;
import com.opuadm.linuxifymc.machine.logs.Levels;
import com.opuadm.linuxifymc.machine.shell.SudoContext;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;

public final class FsNodes {
    private FsNodes() {
    }

    public static final String BINARY_MAGIC = (char) 0x7F + "LMC";
    public static final String BINARY_VERSION = "v=1";

    static boolean canExecute(FakeFS fs, String path) {
        if (fs.playerUuid == null || path == null || path.isEmpty()) return false;
        try {
            String resolved = FsPaths.resolve(fs, path);
            if (resolved == null) return false;
            var rows = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), resolved);
            if (rows == null || rows.isEmpty()) return false;
            var row = rows.getFirst();
            return FsMeta.hasPermissions(row.get(2).toString(), row.getFirst().toString(), row.get(1).toString(),
                    SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "x");
        } catch (Exception e) {
            return false;
        }
    }

    static void txWriteFile(FakeFS fs, String path, String newContent, boolean append) {
        if (fs.playerUuid == null) return;
        try {
            FakeFS.DB.runInTransaction(conn -> {
                Object lenObj = FakeFS.DB.singleValueQuery("SELECT length(content) FROM fs_files WHERE player_uuid = ? AND path = ?",
                        fs.playerUuid.toString(), path);
                if (lenObj == null && !append) {
                    FakeFS.DB.executeUpdate("INSERT INTO fs_files (player_uuid, path, owner, group_name, permissions, content) VALUES (?, ?, ?, ?, ?, ?)",
                            fs.playerUuid.toString(), path, fs.plr, FakeFS.defaultGroup, "644", "");
                    lenObj = 0;
                }
                if (lenObj == null) {
                    if (LinuxifyMC.debugMode) {
                        FakeFS.logger.warning("E: File not found or could not be updated: " + path);
                    }
                    return;
                }

                long oldLen = (lenObj instanceof Number n) ? n.longValue() : 0L;
                long newLen;

                if (append) {
                    FakeFS.DB.executeUpdate("UPDATE fs_files SET content = COALESCE(content, '') || ? WHERE player_uuid = ? AND path = ?",
                            newContent != null ? newContent : "", fs.playerUuid.toString(), path);
                    newLen = oldLen + (newContent != null ? newContent.length() : 0);
                } else {
                    FakeFS.DB.executeUpdate("UPDATE fs_files SET content = ? WHERE player_uuid = ? AND path = ?",
                            newContent, fs.playerUuid.toString(), path);
                    newLen = (newContent != null ? newContent.length() : 0);
                }

                long delta = newLen - oldLen;
                FsDisk.adjustUserFileBytes(fs, delta);
                FsDisk.updateDiskSpaceUsage(fs);

                FakeFS.DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                        fs.playerUuid.toString(), fs.plr, FakeFS.FS_VER, fs.totalDiskSpaceUsed, fs.diskSpaceFree, fs.CurDir);
            });
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: txWriteFile failed: " + e.getMessage(), e);
        }
    }

    static boolean makeLink(FakeFS fs, String linkPath, String target, String owner) {
        if (fs.playerUuid == null) {
            FakeFS.logger.warning("W: makeLink called on uninitialized FakeFS (playerUuid == null). Player name: " + fs.plr);
            return false;
        }
        try {
            if (linkPath == null || linkPath.isEmpty() || target == null || target.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: makeLink requires a link path and a target.");
                }
                return false;
            }
            if (!linkPath.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                linkPath = (base.equals("/") ? "" : base) + "/" + linkPath;
            }
            linkPath = FsPaths.collapse(linkPath);
            if (linkPath.equals("/")) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: makeLink refused: cannot link root.");
                }
                return false;
            }
            String parent = FsPaths.resolveParent(fs, linkPath);
            if (parent == null) {
                return false;
            }
            var pr = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: makeLink: parent not found: " + parent);
                }
                return false;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.getFirst();
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (FsMeta.lacksPermissions(parentPerms, parentOwner, parentGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w") && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: makeLink: permission denied for player=" + fs.plr + " parent=" + parent + " perms=" + parentPerms);
                }
                CustomLogger.Log(FakeFS.getPlr(fs), Levels.GENERAL, MessageFormat.format("[    {0}] makeLink: permission denied! parent={1} perms={2}", Timer.getStamp(fs.playerUuid), parent, parentPerms));
                return false;
            }
            String leaf = linkPath.substring(linkPath.lastIndexOf('/') + 1);
            String full = parent.equals("/") ? "/" + leaf : parent + "/" + leaf;

            Object dirCnt = FakeFS.DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), full);
            Object fileCnt = FakeFS.DB.singleValueQuery("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), full);
            long dirs = (dirCnt instanceof Number n) ? n.longValue() : 0L;
            long files = (fileCnt instanceof Number n) ? n.longValue() : 0L;
            if (dirs > 0 || files > 0 || FsPaths.linkTarget(fs, full) != null) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: makeLink: path already exists: " + full);
                }
                return false;
            }

            if (owner == null || owner.isEmpty()) {
                owner = fs.plr != null ? fs.plr.toLowerCase() : "root";
            }
            owner = SudoContext.getEffectiveUser(fs.playerUuid, owner);

            FakeFS.DB.executeUpdate("INSERT INTO fs_links (player_uuid, path, target, owner, group_name, permissions) VALUES (?, ?, ?, ?, ?, ?)",
                    fs.playerUuid.toString(), full, target, owner, FakeFS.defaultGroup, "777");

            if (LinuxifyMC.debugMode) {
                FakeFS.logger.info("I: makeLink inserted: " + full + " -> " + target);
            }
            return true;
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while creating link: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while creating link! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
            return false;
        }
    }

    static String readLink(FakeFS fs, String path) {
        if (fs.playerUuid == null) return null;
        try {
            if (path == null || path.isEmpty()) return null;
            if (!path.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = FsPaths.collapse(path);
            return FsPaths.linkTarget(fs, path);
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while reading link: " + e.getMessage(), e);
            return null;
        }
    }

    static boolean deleteLink(FakeFS fs, String path) {
        if (fs.playerUuid == null || path == null) return false;
        try {
            return FakeFS.DB.executeUpdate("DELETE FROM fs_links WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    static List<String> binaryNames(FakeFS fs) {
        List<String> names = new ArrayList<>();
        if (fs.playerUuid == null) return names;
        try {
            var rows = FakeFS.DB.query(
                    "SELECT path FROM fs_files WHERE player_uuid = ? AND ((path LIKE ? ESCAPE '\\' AND path NOT LIKE ? ESCAPE '\\') OR (path LIKE ? ESCAPE '\\' AND path NOT LIKE ? ESCAPE '\\')) " +
                            "UNION SELECT path FROM fs_links WHERE player_uuid = ? AND ((path LIKE ? ESCAPE '\\' AND path NOT LIKE ? ESCAPE '\\') OR (path LIKE ? ESCAPE '\\' AND path NOT LIKE ? ESCAPE '\\'))",
                    fs.playerUuid.toString(), "/usr/bin/%", "/usr/bin/%/%", "/bin/%", "/bin/%/%",
                    fs.playerUuid.toString(), "/usr/bin/%", "/usr/bin/%/%", "/bin/%", "/bin/%/%");
            if (rows == null) return names;
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (var r : rows) {
                String p = r.getFirst().toString();
                seen.add(p.substring(p.lastIndexOf('/') + 1));
            }
            names.addAll(seen);
            Collections.sort(names);
        } catch (Exception e) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.fine("E: binaryNames failed for player=" + fs.plr + ": " + e.getMessage());
            }
        }
        return names;
    }

    static void makeDir(FakeFS fs, String path, String owner, String perms) {
        if (fs.playerUuid == null) {
            FakeFS.logger.warning("W: makeDir called on uninitialized FakeFS (playerUuid == null). Attempting to recover using player name: " + fs.plr);
            return;
        }
        try {
            if (path != null && !path.startsWith("/")) { String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir; path = (base.equals("/") ? "" : base) + "/" + path; }
            path = path == null ? null : path.replaceAll("/+", "/");
            path = FsPaths.getString(fs, path);
            if (path == null) return;

            if (owner == null || owner.isEmpty()) {
                owner = fs.plr != null ? fs.plr.toLowerCase() : "root";
            }
            owner = SudoContext.getEffectiveUser(fs.playerUuid, owner);
            if (perms == null || perms.length() != 3) {
                perms = "755";
            }

            Object cntObj = FakeFS.DB.singleValueQuery("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            long dirCount = cntObj instanceof Number ? ((Number) cntObj).longValue() : 0L;
            if (dirCount > 0) return;
            if (FsPaths.linkTarget(fs, path) != null) return;

            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            if (parent.isEmpty()) parent = "/";
            var pr = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: makeDir: parent not found: " + parent);
                }
                CustomLogger.Log(FakeFS.getPlr(fs), Levels.GENERAL, MessageFormat.format("[    {0}] makeDir: parent not found: {1}", Timer.getStamp(fs.playerUuid), parent));
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.getFirst();
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (FsMeta.lacksPermissions(parentPerms, parentOwner, parentGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w") && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: makeDir: permission denied for player=" + fs.plr + " parent=" + parent + " perms=" + parentPerms);
                }
                CustomLogger.Log(FakeFS.getPlr(fs), Levels.GENERAL, MessageFormat.format("[    {0}] makeDir: permission denied! parent={1} perms={2}", Timer.getStamp(fs.playerUuid), parent, parentPerms));
                return;
            }

            FakeFS.DB.executeUpdate("INSERT INTO fs_dirs (player_uuid, path, owner, group_name, permissions) VALUES (?, ?, ?, ?, ?)",
                    fs.playerUuid.toString(), path, owner, FakeFS.defaultGroup, perms);

            if (LinuxifyMC.debugMode) {
                FakeFS.logger.info("I: makeDir inserted: " + path + " owner=" + owner + " perms=" + perms);
            }

            FakeFS.DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                    fs.playerUuid.toString(), fs.plr, FakeFS.FS_VER, fs.totalDiskSpaceUsed, fs.diskSpaceFree, fs.CurDir);

            fs.changePermissions(path, perms);
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while creating directory: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while creating directory! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
        }
    }

    static void makeFile(FakeFS fs, String path, String owner, String perms, String content) {
        if (fs.playerUuid == null) {
            FakeFS.logger.warning("W: makeFile called on uninitialized FakeFS (playerUuid == null). Player name: " + fs.plr);
            return;
        }
        try {
            if (path != null && !path.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = path == null ? null : path.replaceAll("/+", "/");

            path = FsPaths.getString(fs, path);
            if (path == null) {
                return;
            }

            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
            if (parent.isEmpty()) parent = "/";
            var pr = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: makeFile: parent not found: " + parent);
                }
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.getFirst();
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (FsMeta.lacksPermissions(parentPerms, parentOwner, parentGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w") && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: makeFile: permission denied for player=" + fs.plr + " parent=" + parent + " perms=" + parentPerms);
                }
                CustomLogger.Log(FakeFS.getPlr(fs), Levels.GENERAL, MessageFormat.format("[    {0}] makeFile: permission denied! parent={1} perms={2}", Timer.getStamp(fs.playerUuid), parent, parentPerms));
                return;
            }

            if (owner == null || owner.isEmpty()) {
                owner = fs.plr != null ? fs.plr.toLowerCase() : "root";
            }
            owner = SudoContext.getEffectiveUser(fs.playerUuid, owner);
            if (perms == null || perms.length() != 3) {
                perms = "644";
            }

            if (content != null && content.length() > fs.diskSpaceFree) return;

            Object countObj = FakeFS.DB.singleValueQuery("SELECT COUNT(*) AS cnt FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            if (countObj == null) return;
            long fileCount = (countObj instanceof Number) ? ((Number) countObj).longValue() : 0L;
            if (fileCount > 0) return;
            if (FsPaths.linkTarget(fs, path) != null) return;

            FakeFS.DB.executeUpdate("INSERT INTO fs_files (player_uuid, path, owner, group_name, permissions, content) VALUES (?, ?, ?, ?, ?, ?)",
                    fs.playerUuid.toString(), path, owner, FakeFS.defaultGroup, perms, content);

            if (LinuxifyMC.debugMode) {
                FakeFS.logger.info("I: makeFile inserted: " + path + " owner=" + owner + " perms=" + perms + " size=" + (content==null?0:content.length()));
            }

            fs.changePermissions(path, perms);
            if (content != null) {
                FsDisk.adjustUserFileBytes(fs, content.length());
                FsDisk.updateDiskSpaceUsage(fs);
            }
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while creating file: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while creating file! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
        }
    }

    static void deleteFile(FakeFS fs, String path) {
        if (fs.playerUuid == null) return;
        if (path == null || path.isEmpty()) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.warning("E: deleteFile requires a valid path.");
            }
            return;
        }
        try {
            if (!path.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            String rawParent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
            if (rawParent.isEmpty()) rawParent = "/";
            String parent = FsPaths.resolve(fs, rawParent);
            if (parent == null) {
                return;
            }
            var pr = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: deleteFile: parent not found: " + parent);
                }
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.getFirst();
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (FsMeta.lacksPermissions(parentPerms, parentOwner, parentGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w") && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: deleteFile: permission denied for player=" + fs.plr + " parent=" + parent + " perms=" + parentPerms);
                }
                CustomLogger.Log(FakeFS.getPlr(fs), Levels.GENERAL, MessageFormat.format("[    {0}] deleteFile: permission denied! parent={1} perms={2}", Timer.getStamp(fs.playerUuid), parent, parentPerms));
                return;
            }
            String leaf = path.substring(path.lastIndexOf('/') + 1);
            String target = parent.equals("/") ? "/" + leaf : parent + "/" + leaf;
            Object lenObj = FakeFS.DB.singleValueQuery("SELECT length(content) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), target);
            long oldLen = (lenObj instanceof Number n) ? n.longValue() : 0L;

            int deleted = FakeFS.DB.executeUpdate("DELETE FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), target);
            if (deleted > 0) {
                FsDisk.adjustUserFileBytes(fs, -oldLen);
                FsDisk.updateDiskSpaceUsage(fs);
                FakeFS.DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                        fs.playerUuid.toString(), fs.plr, FakeFS.FS_VER, fs.totalDiskSpaceUsed, fs.diskSpaceFree, fs.CurDir);
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.info("I: File deleted: " + target);
                }
            } else if (deleteLink(fs, path)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.info("I: Link deleted: " + path);
                }
            } else {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("I: deleteFile: file not found: " + target);
                }
            }
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: deleteFile failed: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while deleting file! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
        }
    }

    static void deleteDir(FakeFS fs, String path, boolean recursive, boolean force) {
        if (fs.playerUuid == null) return;
        if (path == null || path.isEmpty()) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.warning("E: deleteDir requires a valid path.");
            }
            return;
        }
        try {
            if (!path.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            if (FsPaths.linkTarget(fs, path) != null) {
                String linkParentRaw = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
                if (linkParentRaw.isEmpty()) linkParentRaw = "/";
                String linkParent = FsPaths.resolve(fs, linkParentRaw);
                if (linkParent == null) {
                    return;
                }
                var lpr = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                        fs.playerUuid.toString(), linkParent);
                if (lpr == null || lpr.isEmpty()) {
                    return;
                }
                List<Object> lprow = lpr.getFirst();
                if (FsMeta.lacksPermissions((String) lprow.get(2), (String) lprow.getFirst(), (String) lprow.get(1), SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w") && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                    return;
                }
                if (deleteLink(fs, path) && LinuxifyMC.debugMode) {
                    FakeFS.logger.info("I: Link deleted: " + path);
                }
                return;
            }

            path = FsPaths.resolve(fs, path);
            if (path == null) {
                return;
            }

            var dirCheck = FakeFS.DB.query("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            long dirCount = 0;
            if (dirCheck != null && !dirCheck.isEmpty()) {
                dirCount = ((Number) dirCheck.getFirst().getFirst()).longValue();
            }
            if (dirCount == 0) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: deleteDir: Directory not found: " + path);
                }
                return;
            }

            String like = path.equals("/") ? "/%" : Database.escapeLike(path) + "/%";

            var contentRows = FakeFS.DB.query(
                    "SELECT (SELECT COUNT(*) FROM fs_dirs d WHERE d.player_uuid = ? AND d.path LIKE ? ESCAPE '\\') AS dircnt, " +
                            "(SELECT COALESCE(sum(length(content)),0) FROM fs_files f WHERE f.player_uuid = ? AND f.path LIKE ? ESCAPE '\\') AS filesize, " +
                            "(SELECT COUNT(*) FROM fs_files f WHERE f.player_uuid = ? AND f.path LIKE ? ESCAPE '\\') AS filecnt, " +
                            "(SELECT COUNT(*) FROM fs_links l WHERE l.player_uuid = ? AND l.path LIKE ? ESCAPE '\\') AS linkcnt",
                    fs.playerUuid.toString(), like,
                    fs.playerUuid.toString(), like,
                    fs.playerUuid.toString(), like,
                    fs.playerUuid.toString(), like
            );

            long dirCntInside = 0;
            long totalFilesSize = 0;
            long fileCnt = 0;
            long linkCnt = 0;
            if (contentRows != null && !contentRows.isEmpty()) {
                var row = contentRows.getFirst();
                dirCntInside = ((Number) row.getFirst()).longValue();
                Object sizeObj = row.get(1);
                totalFilesSize = (sizeObj instanceof Number n) ? n.longValue() : 0L;
                fileCnt = ((Number) row.get(2)).longValue();
                linkCnt = ((Number) row.get(3)).longValue();
            }

            if (!recursive && (dirCntInside > 0 || fileCnt > 0 || linkCnt > 0)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: deleteDir refused: directory not empty: " + path);
                }
                return;
            }

            if (recursive) {
                String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
                if (parent.isEmpty()) parent = "/";
                var pr = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                        fs.playerUuid.toString(), parent);
                if (pr == null || pr.isEmpty()) {
                    FakeFS.logger.fine("E: deleteDir: parent not found: " + parent);
                    return;
                }
                List<Object> prow = pr.getFirst();
                String parentOwner = (String) prow.getFirst();
                String parentGroup = (String) prow.get(1);
                String parentPerms = (String) prow.get(2);
                if (FsMeta.lacksPermissions(parentPerms, parentOwner, parentGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w") && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                    if (LinuxifyMC.debugMode) {
                        FakeFS.logger.fine("E: deleteDir: permission denied for player=" + fs.plr + " parent=" + parent + " perms=" + parentPerms);
                    }
                    CustomLogger.Log(FakeFS.getPlr(fs), Levels.GENERAL, MessageFormat.format("[    {0}] deleteDir: permission denied! parent={1} perms={2}", Timer.getStamp(fs.playerUuid), parent, parentPerms));
                    return;
                }
                if (totalFilesSize > 0) {
                    FsDisk.adjustUserFileBytes(fs, -totalFilesSize);
                }

                FakeFS.DB.executeUpdate("DELETE FROM fs_files WHERE player_uuid = ? AND path LIKE ? ESCAPE '\\'",
                        fs.playerUuid.toString(), like);
                FakeFS.DB.executeUpdate("DELETE FROM fs_links WHERE player_uuid = ? AND path LIKE ? ESCAPE '\\'",
                        fs.playerUuid.toString(), like);
                FakeFS.DB.executeUpdate("DELETE FROM fs_dirs WHERE player_uuid = ? AND path LIKE ? ESCAPE '\\'",
                        fs.playerUuid.toString(), like);
            }

            FakeFS.DB.executeUpdate("DELETE FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);

            FsDisk.updateDiskSpaceUsage(fs);
            FakeFS.DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                    fs.playerUuid.toString(), fs.plr, FakeFS.FS_VER, fs.totalDiskSpaceUsed, fs.diskSpaceFree, fs.CurDir);

            if (LinuxifyMC.debugMode) {
                FakeFS.logger.info("I: Directory deleted: " + path + (force ? " (forced)" : ""));
            }
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: deleteDir failed: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while deleting directory! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
        }
    }

    static String getDir(FakeFS fs, String path) {
        if (fs.playerUuid == null) return null;
        try {
            if (path == null || path.isEmpty()) {
                return null;
            }
            if (!path.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            path = FsPaths.resolve(fs, path);
            if (path == null) {
                return null;
            }

            var result = FakeFS.DB.query("SELECT path FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            if (result == null || result.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: Directory not found for player=" + fs.plr + ": " + path);
                }
                return null;
            }
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while retrieving the directory: " + e.getMessage(), e);
            return null;
        }
        return path;
    }

    static String getFile(FakeFS fs, String path) {
        if (fs.playerUuid == null) return null;
        try {
            if (path == null || path.isEmpty()) return null;
            if (!path.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            path = FsPaths.resolve(fs, path);
            if (path == null) return null;

            var result = FakeFS.DB.query("SELECT content FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            if (result == null || result.isEmpty()) return null;

            Object content = result.getFirst().getFirst();
            return content != null ? content.toString() : null;
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while reading file: " + e.getMessage(), e);
            return null;
        }
    }

    static String listCurrentDir(FakeFS fs, String path, boolean showHidden, boolean showDetails) {
        if (fs.playerUuid == null) return "";
        String d;
        if (path == null || path.isEmpty()) {
            d = fs.CurDir;
        } else {
            Deque<String> stack = FsPaths.getStrings(fs, path);
            d = "/" + String.join("/", stack);
            if (d.length() > 1 && d.endsWith("/")) d = d.substring(0, d.length() - 1);
        }
        d = d == null || d.isEmpty() ? "/" : d.replaceAll("/+", "/");
        d = FsPaths.resolve(fs, d);
        if (d == null) return "";
        String like = d.equals("/") ? "/%" : Database.escapeLike(d) + "/%";
        String notLike = d.equals("/") ? "/%/%" : Database.escapeLike(d) + "/%/%";
        var rows = FakeFS.DB.query(
                "SELECT 'D' as t, path, owner, permissions, '' AS target FROM fs_dirs WHERE player_uuid=? AND path LIKE ? ESCAPE '\\' AND path NOT LIKE ? ESCAPE '\\' " +
                        "UNION ALL SELECT 'F', path, owner, permissions, '' FROM fs_files WHERE player_uuid=? AND path LIKE ? ESCAPE '\\' AND path NOT LIKE ? ESCAPE '\\' " +
                        "UNION ALL SELECT 'L', path, owner, permissions, target FROM fs_links WHERE player_uuid=? AND path LIKE ? ESCAPE '\\' AND path NOT LIKE ? ESCAPE '\\'",
                fs.playerUuid.toString(), like, notLike, fs.playerUuid.toString(), like, notLike, fs.playerUuid.toString(), like, notLike);
        if (rows == null) return "";
        StringBuilder out = new StringBuilder();
        for (var r : rows) {
            String p = r.get(1).toString();
            String name = p.substring(p.lastIndexOf('/') + 1);
            if (!showHidden && name.startsWith(".")) continue;
            boolean isLink = "L".equals(r.getFirst().toString());
            if (showDetails) {
                if (isLink) {
                    out.append(String.format("%s %s -> %s owner=%s perms=%s", r.getFirst(), p, r.get(4), r.get(2), r.get(3)));
                } else {
                    out.append(String.format("%s %s owner=%s perms=%s", r.getFirst(), p, r.get(2), r.get(3)));
                }
            } else {
                out.append(name);
                if (r.getFirst().equals("D")) out.append("/");
                else if (isLink) out.append(" -> ").append(r.get(4));
            }
            out.append('\n');
        }
        return out.toString();
    }

    static void writeFile(FakeFS fs, String path, String content) {
        if (fs.playerUuid == null) return;
        try {
            if (path == null || path.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: Path cannot be null or empty.");
                }
                return;
            }
            if (!path.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = path.replaceAll("/+", "/");
            path = FsPaths.getString(fs, path);
            if (path == null) return;

            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            if (parent.isEmpty()) parent = "/";
            var pr = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: writeFile: parent not found: " + parent);
                }
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.getFirst();
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (FsMeta.lacksPermissions(parentPerms, parentOwner, parentGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w") && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: writeFile: permission denied for player=" + fs.plr + " parent=" + parent + " perms=" + parentPerms);
                }
                return;
            }
            txWriteFile(fs, path, content, false);
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while writing to the file: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while writing to file! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
        }
    }

    static void appendFile(FakeFS fs, String path, String content) {
        if (fs.playerUuid == null) return;
        try {
            if (path == null || path.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: Path cannot be null or empty.");
                }
                return;
            }

            if (!path.startsWith("/")) {
                String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
                path = (base.equals("/") ? "" : base) + "/" + path;
            }
            path = path.replaceAll("/+", "/");
            path = FsPaths.getString(fs, path);
            if (path == null) return;

            Object existsCnt = FakeFS.DB.singleValueQuery("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            long cnt = (existsCnt instanceof Number n) ? n.longValue() : 0L;
            if (cnt == 0L) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: File not found or could not be updated: " + path);
                }
                return;
            }
            String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
            if (parent.isEmpty()) parent = "/";
            var pr = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), parent);
            if (pr == null || pr.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: appendFile: parent not found: " + parent);
                }
                return;
            }
            List<Object> prow = pr.getFirst();
            String parentOwner = (String) prow.getFirst();
            String parentGroup = (String) prow.get(1);
            String parentPerms = (String) prow.get(2);
            if (FsMeta.lacksPermissions(parentPerms, parentOwner, parentGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w") && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: appendFile: permission denied for player=" + fs.plr + " parent=" + parent + " perms=" + parentPerms);
                }
                return;
            }
            txWriteFile(fs, path, content, true);
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while appending to the file: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while appending to file! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
        }
    }
}
