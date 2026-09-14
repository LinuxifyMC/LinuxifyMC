package com.opuadm.linuxifymc.machine.fs;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.clock.Timer;
import com.opuadm.linuxifymc.machine.logs.CustomLogger;
import com.opuadm.linuxifymc.machine.logs.Levels;
import com.opuadm.linuxifymc.machine.shell.SudoContext;

import java.text.MessageFormat;
import java.util.logging.Level;

public final class FsMeta {
    private FsMeta() {
    }

    public static boolean hasPermissions(String perms, String owner, String group, String subject, String requiredPerm) {
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

    public static boolean lacksPermissions(String perms, String owner, String group, String subject, String requiredPerm) {
        return !hasPermissions(perms, owner, group, subject, requiredPerm);
    }

    static void changePermissions(FakeFS fs, String path, String newPerms) {
        if (fs.playerUuid == null) return;
        try {
            if (path == null || path.isEmpty() || newPerms == null || newPerms.length() != 3) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: Path cannot be null or empty, or new permissions cannot be null or invalid.");
                }
                return;
            }
            path = path.replaceAll("/+", "/");

            path = FsPaths.resolve(fs, path);
            if (path == null) {
                return;
            }

            String symbolicPerms = ConvertPerms.octalToSymbolic(newPerms);

            var dirRes = FakeFS.DB.query("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            long dirCount = 0;
            if (dirRes != null && !dirRes.isEmpty()) {
                dirCount = ((Number) dirRes.getFirst().getFirst()).longValue();
            }

            var fileRes = FakeFS.DB.query("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            long fileCount = 0;
            if (fileRes != null && !fileRes.isEmpty()) {
                fileCount = ((Number) fileRes.getFirst().getFirst()).longValue();
            }

            String curOwner = null;
            String curGroup = null;
            String curPerms = null;
            var metaRes = FakeFS.DB.query("SELECT owner, group_name, permissions FROM fs_dirs WHERE player_uuid = ? AND path = ? " +
                            "UNION ALL SELECT owner, group_name, permissions FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path, fs.playerUuid.toString(), path);
            if (metaRes != null && !metaRes.isEmpty()) {
                var meta = metaRes.getFirst();
                curOwner = meta.getFirst() != null ? meta.getFirst().toString() : null;
                curGroup = meta.get(1) != null ? meta.get(1).toString() : null;
                curPerms = meta.get(2) != null ? meta.get(2).toString() : null;
            }

            if ((curPerms == null || lacksPermissions(curPerms, curOwner, curGroup, SudoContext.getEffectiveUser(fs.playerUuid, fs.plr), "w"))
                    && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: changePermissions: permission denied for player=" + fs.plr + " path=" + path);
                }
                return;
            }

            if (dirCount > 0) {
                FakeFS.DB.executeUpdate("UPDATE fs_dirs SET permissions = ? WHERE player_uuid = ? AND path = ?",
                        newPerms, fs.playerUuid.toString(), path);
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.info("I: Directory permissions changed for " + path + " to " + newPerms + " (" + symbolicPerms + ")");
                }
            } else if (fileCount > 0) {
                FakeFS.DB.executeUpdate("UPDATE fs_files SET permissions = ? WHERE player_uuid = ? AND path = ?",
                        newPerms, fs.playerUuid.toString(), path);
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.info("I: File permissions changed for " + path + " to " + newPerms + " (" + symbolicPerms + ")");
                }
            } else {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: Path not found: " + path);
                }
            }
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: An error occurred while changing permissions: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while changing permissions! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
        }
    }

    static void changeOwner(FakeFS fs, String path, String newOwner) {
        if (fs.playerUuid == null) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.warning("E: changeOwner requires player UUID.");
            }
            return;
        }
        if (path == null || path.isEmpty() || newOwner == null || newOwner.isEmpty()) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.warning("E: changeOwner requires a valid path and owner.");
            }
            return;
        }
        try {
            path = path.replaceAll("/+", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            path = FsPaths.resolve(fs, path);
            if (path == null) {
                return;
            }

            var dirRes = FakeFS.DB.query("SELECT COUNT(*) FROM fs_dirs WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            long dirCount = 0;
            if (dirRes != null && !dirRes.isEmpty()) {
                dirCount = ((Number) dirRes.getFirst().getFirst()).longValue();
            }

            var fileRes = FakeFS.DB.query("SELECT COUNT(*) FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path);
            long fileCount = 0;
            if (fileRes != null && !fileRes.isEmpty()) {
                fileCount = ((Number) fileRes.getFirst().getFirst()).longValue();
            }

            var ownerRes = FakeFS.DB.query("SELECT owner FROM fs_dirs WHERE player_uuid = ? AND path = ? UNION ALL SELECT owner FROM fs_files WHERE player_uuid = ? AND path = ?",
                    fs.playerUuid.toString(), path, fs.playerUuid.toString(), path);
            String curOwner = null;
            if (ownerRes != null && !ownerRes.isEmpty()) {
                curOwner = ownerRes.getFirst().getFirst().toString();
            }
            if ((curOwner == null || !curOwner.equalsIgnoreCase(SudoContext.getEffectiveUser(fs.playerUuid, fs.plr))) && SudoContext.lacksSudoPrivileges(fs.playerUuid)) {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.fine("E: changeOwner: permission denied for player=" + fs.plr + " path=" + path);
                }
                return;
            }

            if (dirCount > 0) {
                FakeFS.DB.executeUpdate("UPDATE fs_dirs SET owner = ? WHERE player_uuid = ? AND path = ?",
                        newOwner, fs.playerUuid.toString(), path);
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.info("I: Directory owner changed for " + path + " to " + newOwner);
                }
            } else if (fileCount > 0) {
                FakeFS.DB.executeUpdate("UPDATE fs_files SET owner = ? WHERE player_uuid = ? AND path = ?",
                        newOwner, fs.playerUuid.toString(), path);
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.info("I: File owner changed for " + path + " to " + newOwner);
                }
            } else {
                if (LinuxifyMC.debugMode) {
                    FakeFS.logger.warning("E: changeOwner: Path not found: " + path);
                }
            }
        } catch (Exception e) {
            FakeFS.logger.log(Level.WARNING, "E: changeOwner failed: " + e.getMessage(), e);
            CustomLogger.Log(FakeFS.getPlr(fs), Levels.WARNING, MessageFormat.format("[    {0}] Error while changing owner! Error: ${1} ${2}", Timer.getStamp(fs.playerUuid), e.getMessage(), e));
        }
    }
}
