package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import com.opuadm.linuxifymc.commands.cli.ArgUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class RM {
    private static final Logger LOG = Logger.getLogger("LinuxifyMC");

    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        boolean recursive = false;
        boolean force = false;
        String path = ArgUtils.getPositional(args, 1);

        if (ArgUtils.hasFlag(args, "--force") || ArgUtils.hasFlag(args, "-f") || ArgUtils.hasFlag(args, "-F")) {
            force = true;
        }

        if (path == null) {
            sender.sendMessage("Usage: rm [-rRf] PATH...");
            return true;
        }

        if (path.startsWith("~")) {
            path = "/home/" + player.getName() + path.substring(1);
        }

        String normPath = getString(fs, path);

        String fileContent = fs.getFile(normPath);
        if (fileContent != null) {
            try {
                LOG.fine("rm: user=" + player.getName() + " path=" + path + " cwd=" + fs.getCurrentDir());
                fs.deleteFile(normPath);
                LOG.fine("rm: finished deleteFile for " + normPath);
            } catch (Exception e) {
                if (!force) sender.sendMessage("rm: cannot remove '" + path + "': " + e.getMessage());
                LOG.warning("rm: exception while deleting file " + normPath + ": " + e.getMessage());
            }
            return true;
        }

        if (!force) sender.sendMessage("rm: cannot remove '" + path + "': No such file or directory");

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