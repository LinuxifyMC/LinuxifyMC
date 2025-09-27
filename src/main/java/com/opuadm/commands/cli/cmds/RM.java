package com.opuadm.commands.cli.cmds;

import com.opuadm.machine.fs.FakeFS;
import com.opuadm.LinuxifyMC;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

@SuppressWarnings("unused")
public class RM {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        boolean recursive = false;
        String path = null;

        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if (args[i].contains("r") || args[i].contains("R")) {
                    recursive = true;
                }
                continue;
            }
            path = args[i];
        }

        if (path == null) {
            sender.sendMessage("Usage: rm [-r] <path>");
            return true;
        }

        String normPath = path.replaceAll("/+", "/");
        if (normPath.length() > 1 && normPath.endsWith("/")) normPath = normPath.substring(0, normPath.length() - 1);

        String fileContent = fs.getFile(normPath);
        String dirPath = fs.getDir(normPath);

        if (dirPath != null && fileContent == null) {
            if (!recursive) {
                sender.sendMessage(LinuxifyMC.shellname + ": rm: " + path + ": is a directory");
                return true;
            }
            try {
                fs.deleteDir(normPath, true);
                sender.sendMessage("");
            } catch (Exception e) {
                sender.sendMessage(LinuxifyMC.shellname + ": rm: " + path + ": Failed to remove");
            }
            return true;
        }

        if (fileContent != null) {
            try {
                fs.deleteFile(normPath);
                sender.sendMessage("");
            } catch (Exception e) {
                sender.sendMessage(LinuxifyMC.shellname + ": rm: " + path + ": Failed to remove");
            }
            return true;
        }

        sender.sendMessage(LinuxifyMC.shellname + ": rm: " + path + ": No such file or directory");

        return true;
    }
}
