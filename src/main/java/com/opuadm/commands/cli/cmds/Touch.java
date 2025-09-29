package com.opuadm.commands.cli.cmds;

import com.opuadm.LinuxifyMC;
import com.opuadm.machine.fs.FakeFS;
import com.opuadm.machine.fs.ConvertPerms;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class Touch {
    @SuppressWarnings("unused")
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: touch <filename>");
            return true;
        }
        String fileName = args[1];

        try {
            fs.makeFile(fileName, player.getName(), ConvertPerms.octalToSymbolic("777"), "");
            sender.sendMessage("");
        } catch (Exception e) {
            sender.sendMessage(LinuxifyMC.shellname + ": touch: Failed to touch file '" + fileName + "'");
        }
        return true;
    }
}