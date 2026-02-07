package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.LinuxifyMC;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

@SuppressWarnings("unused")
public class Chmod {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String permissions = ArgUtils.getPositional(args, 1);
        String path = ArgUtils.getPositional(args, 2);
        if (permissions == null || path == null) {
            sender.sendMessage("Usage: chmod [OPTION]... OCTAL-MODE FILE...");
            return true;
        }

        boolean exists = (fs.getDir(path) != null) || (fs.getFile(path) != null);
        if (!exists) {
            sender.sendMessage(LinuxifyMC.shellname + ": chmod: Failed to change permissions for '" + path + "'");
        } else {
            fs.changePermissions(path, permissions);
            sender.sendMessage("");
        }

        return true;
    }
}