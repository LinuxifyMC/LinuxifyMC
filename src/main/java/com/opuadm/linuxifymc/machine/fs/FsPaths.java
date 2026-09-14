package com.opuadm.linuxifymc.machine.fs;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.shell.SudoContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.List;
import java.util.logging.Level;

public final class FsPaths {
    private FsPaths() {
    }

    static final int MAX_LINK_DEPTH = 8;

    public static String collapse(String path) {
        if (path == null || path.isEmpty()) return "/";
        String[] parts = path.replaceAll("/+", "/").split("/");
        Deque<String> out = new java.util.ArrayDeque<>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!out.isEmpty()) out.removeLast();
                continue;
            }
            out.addLast(p);
        }
        if (out.isEmpty()) return "/";
        return "/" + String.join("/", out);
    }

    static String linkTarget(FakeFS fs, String path) {
        if (fs.playerUuid == null || path == null) return null;
        try {
            Object target = FakeFS.DB.singleValueQuery("SELECT target FROM fs_links WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            return target != null ? target.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String resolve(FakeFS fs, String path) {
        if (fs.playerUuid == null || path == null || path.isEmpty()) return null;
        String cur = collapse(path);
        for (int depth = 0; depth < MAX_LINK_DEPTH; depth++) {
            String hit = null;
            String hitTarget = null;
            try {
                var rows = FakeFS.DB.query("SELECT path, target FROM fs_links WHERE player_uuid = ?", fs.playerUuid.toString());
                if (rows != null) {
                    for (var row : rows) {
                        Object linkObj = row.getFirst();
                        Object targetObj = row.get(1);
                        if (linkObj == null || targetObj == null) continue;
                        String linkPath = linkObj.toString();
                        if (cur.equals(linkPath) || cur.startsWith(linkPath + "/")) {
                            if (hit == null || linkPath.length() > hit.length()) {
                                hit = linkPath;
                                hitTarget = targetObj.toString();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
            if (hit == null) return cur;
            String rest = cur.equals(hit) ? "" : cur.substring(hit.length());
            String target = hitTarget;
            if (!target.startsWith("/")) {
                int slash = hit.lastIndexOf('/');
                String parent = slash <= 0 ? "/" : hit.substring(0, slash);
                target = parent.equals("/") ? "/" + target : parent + "/" + target;
            }
            cur = collapse(target + rest);
        }
        return null;
    }

    static String resolveParent(FakeFS fs, String path) {
        if (path == null || path.isEmpty()) return null;
        String clean = collapse(path);
        int slash = clean.lastIndexOf('/');
        String parent = slash <= 0 ? "/" : clean.substring(0, slash);
        return resolve(fs, parent);
    }

    static boolean setCurrentDir(FakeFS fs, String target) {
        if (fs.playerUuid == null) return false;
        if (target == null || target.isEmpty()) return false;
        Deque<String> stack = getStrings(fs, target);
        String finalPath = "/" + String.join("/", stack);
        if (finalPath.length() > 1 && finalPath.endsWith("/")) finalPath = finalPath.substring(0, finalPath.length() - 1);

        String verified = FsNodes.getDir(fs, finalPath);
        if (verified == null) {
            FakeFS.logger.fine("E: setCurrentDir failed: target=" + target + " resolved=" + finalPath);
            return false;
        }

        try {
            fs.CurDir = verified;
            FakeFS.DB.executeUpdate("INSERT OR REPLACE INTO fs_saves (player_uuid, player_name, fs_version, disk_space_used, disk_space_free, current_dir) VALUES (?, ?, ?, ?, ?, ?)",
                    fs.playerUuid.toString(), fs.plr, FakeFS.FS_VER, fs.totalDiskSpaceUsed, fs.diskSpaceFree, fs.CurDir);
            return true;
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: setCurrentDir persist failed: " + e.getMessage(), e);
            return false;
        }
    }

    @Nullable
    static String getString(FakeFS fs, String path) {
        if (fs.playerUuid == null) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.warning("E: getString refused: playerUuid is null for player=" + fs.plr + " path=" + path);
            }
            return null;
        }
        if (path == null || path.isEmpty()) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.warning("E: getString refused: path null/empty for player=" + fs.plr);
            }
            return null;
        }

        if (!path.startsWith("/")) {
            String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
            path = (base.equals("/") ? "" : base) + "/" + path;
        }
        path = path.replaceAll("/+", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        String parentDir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        if (parentDir.isEmpty() && path.startsWith("/")) parentDir = "/";

        String resolvedParent = resolve(fs, parentDir);
        if (resolvedParent == null) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.fine("E: getString: link resolution failed for player=" + fs.plr + " parent=" + parentDir);
            }
            return null;
        }
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        path = resolvedParent.equals("/") ? "/" + leaf : resolvedParent + "/" + leaf;
        parentDir = resolvedParent;

        if (!parentDir.isEmpty()) {
            var parentResult = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), parentDir);

            if (parentResult == null || parentResult.isEmpty()) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: getString: parent directory not found for player=" + fs.plr + " parent=" + parentDir + " path=" + path);
                }
                return null;
            }

            List<Object> row = parentResult.getFirst();
            String parentOwner = (String) row.getFirst();
            String parentGroup = (String) row.get(1);
            String parentPerms = (String) row.get(2);

            if (FsMeta.lacksPermissions(parentPerms, parentOwner, parentGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w")
                    && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: getString: write permission denied for player=" + fs.plr + " on parent=" + parentDir + " perms=" + parentPerms + " owner=" + parentOwner + " group=" + parentGroup);
                }
                return null;
            }
        }
        return path;
    }

    static @NotNull Deque<String> getStrings(FakeFS fs, String target) {
        String home = "/home/" + fs.plr.toLowerCase();
        String norm = target.trim();
        if (norm.equals("~")) {
            norm = home;
        } else if (norm.startsWith("~/")) {
            norm = home + norm.substring(1);
        }

        if (!norm.startsWith("/")) {
            String base = (fs.CurDir == null || fs.CurDir.isEmpty()) ? "/" : fs.CurDir;
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
}
