package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class RM {
    private static final Logger LOG = Logger.getLogger("LinuxifyMC");

    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        boolean recursive = false;
        boolean force = false;
        String path = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.isEmpty()) continue;

            if ("--".equals(arg)) {
                if (i + 1 < args.length) path = args[i + 1];
                break;
            }

            if (arg.startsWith("--")) {
                if ("--recursive".equalsIgnoreCase(arg)) {
                    recursive = true;
                } else if ("--force".equalsIgnoreCase(arg)) {
                    force = true;
                }
                continue;
            }

            if (arg.startsWith("-") && arg.length() > 1) {
                for (int j = 1; j < arg.length(); j++) {
                    char c = arg.charAt(j);
                    switch (c) {
                        case 'r', 'R' -> recursive = true;
                        case 'f', 'F' -> force = true;
                        default -> {
                        }
                    }
                }
                continue;
            }

            path = arg;
            break;
        }

        if (path == null) {
            sender.sendMessage("Usage: rm [-r] [-f] <path>");
            return true;
        }

        if (path.startsWith("~")) {
            path = "/home/" + player.getName() + path.substring(1);
        }

        String normPath = getString(fs, path);

        String fileContent = fs.getFile(normPath);
        String dirPath = fs.getDir(normPath);

        if (dirPath != null && fileContent == null) {
            boolean passRecursive = recursive || force;
            try {
                LOG.fine("rm: user=" + player.getName() + " path=" + path + " cwd=" + fs.getCurrentDir());
                fs.deleteDir(normPath, passRecursive, force);
                LOG.fine("rm: finished deleteDir for " + normPath);
            } catch (Exception e) {
                if (!force) sender.sendMessage(LinuxifyMC.shellname + ": rm: " + path + ": Failed to remove");
                LOG.warning("rm: exception while deleting dir " + normPath + ": " + e.getMessage());
            }
            return true;
        }

        if (fileContent != null) {
            try {
                LOG.fine("rm: user=" + player.getName() + " path=" + path + " cwd=" + fs.getCurrentDir());
                fs.deleteFile(normPath);
                LOG.fine("rm: finished deleteFile for " + normPath);
            } catch (Exception e) {
                if (!force) sender.sendMessage(LinuxifyMC.shellname + ": rm: " + path + ": Failed to remove");
                LOG.warning("rm: exception while deleting file " + normPath + ": " + e.getMessage());
            }
            return true;
        }

        if (!force) sender.sendMessage(LinuxifyMC.shellname + ": rm: " + path + ": No such file or directory");

        return true;
    }

    private static @NotNull String getString(FakeFS fs, String path) {
        String normPath = getNormPath(fs, path);

        Deque<String> stack = new ArrayDeque<>();
        String[] parts = normPath.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.removeLast();
            } else {
                stack.addLast(part);
            }
        }

        if (stack.isEmpty()) return "/";

        StringBuilder sb = new StringBuilder();
        for (String p : stack) {
            sb.append('/').append(p);
        }
        return sb.toString();
    }

    private static @NotNull String getNormPath(FakeFS fs, String path) {
        String normPath = path.replaceAll("/+", "/");
        if (normPath.length() > 1 && normPath.endsWith("/")) normPath = normPath.substring(0, normPath.length() - 1);

        if (!normPath.startsWith("/")) {
            String cwd = fs.getCurrentDir();
            if (cwd == null || cwd.isEmpty()) cwd = "/";

            cwd = cwd.replaceAll("/+$", "");
            if (!cwd.startsWith("/")) cwd = "/" + cwd;
            if (cwd.equals("/")) normPath = "/" + normPath;
            else normPath = cwd + "/" + normPath;
            normPath = normPath.replaceAll("/+", "/");
            if (normPath.length() > 1 && normPath.endsWith("/")) normPath = normPath.substring(0, normPath.length() - 1);
        }
        return normPath;
    }
}