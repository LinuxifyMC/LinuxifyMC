package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.commands.cli.CliCommand;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Uname implements CliCommand {
    @Override
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        boolean s = ArgUtils.hasFlag(args, "-s") || ArgUtils.hasFlag(args, "--kernel-name");
        boolean v = ArgUtils.hasFlag(args, "-v") || ArgUtils.hasFlag(args, "--kernel-version");
        boolean all = ArgUtils.hasFlag(args, "-a") || ArgUtils.hasFlag(args, "--all");
        boolean help = ArgUtils.hasFlag(args, "--help") || ArgUtils.hasFlag(args, "-h");

        if (all) {
            s = true;
            v = true;
        }

        if (!s && !v && !help) {
            sender.sendMessage(LinuxifyMC.kernelname);
            return true;
        }

        if (help) {
            sender.sendMessage("Usage: uname [OPTION]...");
            sender.sendMessage("");
            sender.sendMessage("Print certain system information.  With no OPTION, same as -s.");
            sender.sendMessage("");
            sender.sendMessage("Options:");
            sender.sendMessage("  -s, --kernel-name     print the kernel name");
            sender.sendMessage("  -v, --kernel-version  print the kernel version");
            sender.sendMessage("  -a, --all             print all information");
            sender.sendMessage("  -h, --help            display this help and exit");
            return true;
        }

        if (s && v) {
            sender.sendMessage(LinuxifyMC.kernelname + " " + LinuxifyMC.kernelver);
        } else if (s) {
            sender.sendMessage(LinuxifyMC.kernelname);
        } else {
            sender.sendMessage(LinuxifyMC.kernelver);
        }
        return true;
    }
}
