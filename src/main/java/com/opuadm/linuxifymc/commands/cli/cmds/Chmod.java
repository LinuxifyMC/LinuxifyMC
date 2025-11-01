package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.login.Login;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class Chmod {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: chmod [OPTION]... OCTAL-MODE FILE...");
            return true;
        }
        String permissions = args[1];
        String path = args[2];

        String userForHome = player.getName();
        var session = Login.getSession(player.getUniqueId());
        if (session != null && session.getCurrentUser() != null && !session.getCurrentUser().isEmpty()) {
            userForHome = session.getCurrentUser();
        }
        if (path.startsWith("~")) {
            path = path.replaceFirst("~", "/home/" + userForHome);
        }

        try {
            fs.changePermissions(path, permissions);
            sender.sendMessage("");
        } catch (Exception e) {
            sender.sendMessage(LinuxifyMC.shellname + ": chmod: Failed to change permissions for '" + path + "'");
        }

        return true;
    }
}