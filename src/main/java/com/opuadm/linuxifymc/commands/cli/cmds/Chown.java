package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class Chown {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: chown [OPTION]... [OWNER][:[GROUP][ FILE...");
            return true;
        }
        String newOwner = args[1];
        String path = args[2];

        try {
            fs.changeOwner(path, newOwner);
            sender.sendMessage("");
        } catch (Exception e) {
            sender.sendMessage(LinuxifyMC.shellname + ": chown: Failed to change owner for '" + path + "'");
        }

        return true;
    }
}
