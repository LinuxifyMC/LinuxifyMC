package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;
import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.util.logging.Logger;

@SuppressWarnings("unused")
public class Mkdir {
    private static final Logger LOG = Logger.getLogger(LinuxifyMC.pluginName);

    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String arg1 = ArgUtils.getPositional(args, 1);
        if (arg1 == null) {
            sender.sendMessage("mkdir: missing operand");
            sender.sendMessage("Try 'mkdir --help' for more information.");
            return true;
        }

        if (arg1.equals("--help") || arg1.equals("-h")) {
            sender.sendMessage("Usage: mkdir [OPTION]... DIRECTORY...");
            sender.sendMessage("Create the DIRECTORY(ies), if they do not already exist.");
            sender.sendMessage("");
            sender.sendMessage("Options:");
            sender.sendMessage("  -h, --help     display this help and exit");
        } else {
            try {
                LOG.fine("mkdir: user=" + player.getName() + " path=" + arg1 + " cwd=" + fs.getCurrentDir());
                fs.makeDir(arg1, player.getName(), "777");
                LOG.fine("mkdir: finished makeDir for " + arg1);
            } catch (Exception e) {
                sender.sendMessage("mkdir: cannot create directory '" + arg1 + "': " + e.getMessage());
                LOG.warning("mkdir: exception while creating directory " + arg1 + ": " + e.getMessage());
            }
        }
        return true;
    }
}
