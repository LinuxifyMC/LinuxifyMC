package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

@SuppressWarnings("unused")
public class Touch {
    private static final Logger LOG = Logger.getLogger("LinuxifyMC");

    @SuppressWarnings("unused")
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: touch <filename>");
            return true;
        }
        String fileName = args[1];

        try {
            LOG.fine("touch: user=" + player.getName() + " path=" + fileName + " cwd=" + fs.getCurrentDir());
            fs.makeFile(fileName, player.getName(), "777", "");
            sender.sendMessage("File touched: " + fileName);
            LOG.fine("touch: finished makeFile for " + fileName);
        } catch (Exception e) {
            sender.sendMessage(LinuxifyMC.shellname + ": touch: Failed to touch file '" + fileName + "'");
            LOG.warning("touch: exception while touching file " + fileName + ": " + e.getMessage());
        }
        return true;
    }
}