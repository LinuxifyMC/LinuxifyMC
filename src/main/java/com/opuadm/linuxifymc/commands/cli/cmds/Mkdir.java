package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.util.logging.Logger;

@SuppressWarnings("unused")
public class Mkdir {
    private static final Logger LOG = Logger.getLogger("LinuxifyMC");

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
        } else {
            try {
                LOG.fine("mkdir: user=" + player.getName() + " path=" + arg1 + " cwd=" + fs.getCurrentDir());
                fs.makeDir(arg1, player.getName(), "755");
                String created = fs.getDir(arg1);
                if (created != null) {
                    sender.sendMessage("Directory created: " + arg1);
                    LOG.fine("mkdir: finished makeDir for " + arg1);
                } else {
                    sender.sendMessage("mkdir: permission denied");
                    sender.sendMessage("Try 'sudo mkdir " + arg1 + "' if you have sudo privileges.");
                    LOG.fine("mkdir: failed to create directory (permission or other): " + arg1);
                }
            } catch (Exception e) {
                sender.sendMessage(LinuxifyMC.shellname + ": mkdir: Failed to create directory '" + arg1 + "'");
                LOG.warning("mkdir: exception while creating dir " + arg1 + ": " + e.getMessage());
            }
        }
        return true;
    }
}