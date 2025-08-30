package com.opuadm.commands.cli.cmds;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import com.opuadm.commands.cli.FakeFS;
import com.opuadm.LinuxifyMC;

@SuppressWarnings("unused")
public class Mkdir {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("mkdir: missing operand");
            sender.sendMessage("Try 'mkdir --help' for more information.");
            return true;
        }
        String arg1 = args[1];

        if (arg1.equals("--help") || arg1.equals("-h")) {
            sender.sendMessage("Usage: mkdir DIRECTORY...");
            sender.sendMessage("Create the DIRECTORY(ies), if they do not already exist.");
            sender.sendMessage("Options:");
            sender.sendMessage("-h, --help     display this help and exit");
            return true;
        } else {
            if (fs.createDirectory(arg1, null, null)) {
                // No Message
            } else {
                sender.sendMessage(LinuxifyMC.shellname + ": mkdir: Failed to create directory '" + arg1 + "'");
            }
            return true;
        }
    }
}
