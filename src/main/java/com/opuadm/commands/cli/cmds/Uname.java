package com.opuadm.commands.cli.cmds;

import com.opuadm.LinuxifyMC;
import com.opuadm.machine.fs.FakeFS;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class Uname {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args.length == 1 || (args.length == 2 && args[1].equals("-s"))) {
            sender.sendMessage(LinuxifyMC.kernelname);
        } else if (args.length == 2 && args[1].equals("-v")) {
            sender.sendMessage(LinuxifyMC.kernelver);
        } else if (args.length == 3 && args[1].equals("-s") && args[2].equals("-v")) {
            sender.sendMessage(LinuxifyMC.kernelname + " " + LinuxifyMC.kernelver);
        } else if (args[1].equals("--help") || args[1].equals("-h")) {
            sender.sendMessage("Usage: uname [OPTION]...");
            sender.sendMessage("Print certain system information.  With no OPTION, same as -s.");
            sender.sendMessage("Options:");
            sender.sendMessage("-s, --kernel-name     print kernel name");
            sender.sendMessage("-v, --kernel-version  print kernel version");
            sender.sendMessage("-h, --help            display this help and exit");
            return true;
        } else {
            sender.sendMessage("uname: invalid option: '" + args[1] + "'");
            sender.sendMessage("Try 'uname --help' for more information.");
            return false;
        }
        return true;
    }
}
