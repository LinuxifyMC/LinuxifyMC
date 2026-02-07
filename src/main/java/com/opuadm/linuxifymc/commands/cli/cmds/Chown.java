package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.login.Login;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class Chown {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String newOwner = ArgUtils.getPositional(args, 1);
        String path = ArgUtils.getPositional(args, 2);

        if (newOwner == null || path == null) {
            sender.sendMessage("Usage: chown [OPTION]... [OWNER][:[GROUP]] FILE...");
            return true;
        }

        String userForHome = player.getName();
        var session = Login.getSession(player.getUniqueId());
        if (session != null && session.getCurrentUser() != null && !session.getCurrentUser().isEmpty()) {
            userForHome = session.getCurrentUser();
        }
        if (path.startsWith("~")) {
            path = path.replaceFirst("~", "/home/" + userForHome);
        }

        try {
            fs.changeOwner(path, newOwner);
        } catch (Exception e) {
            sender.sendMessage(LinuxifyMC.shellname + ": chown: Failed to change owner for '" + path + "': " + e.getMessage());
        }

        return true;
    }
}