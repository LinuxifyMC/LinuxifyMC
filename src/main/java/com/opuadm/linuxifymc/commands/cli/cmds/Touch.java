package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.commands.cli.ArgUtils;
import com.opuadm.linuxifymc.machine.fs.FakeFS;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class Touch {
    @SuppressWarnings("unused")
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String fileName = ArgUtils.getPositional(args, 1);
        if (fileName == null) {
            sender.sendMessage("Usage: touch <filename>");
            return true;
        }

        String owner = (player != null) ? player.getName() : sender.getName();
        fs.makeFile(fileName, owner, "644", "");
        sender.sendMessage("");
        return true;
    }
}