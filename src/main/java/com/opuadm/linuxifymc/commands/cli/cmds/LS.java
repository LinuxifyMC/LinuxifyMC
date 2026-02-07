package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class LS {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String path = fs.getCurrentDir();
        boolean showHidden = ArgUtils.hasFlag(args, "-a");
        boolean showDetails = ArgUtils.hasFlag(args, "-l") || ArgUtils.hasFlag(args, "-o");

        String p = ArgUtils.getPositional(args, 1);
        if (p != null) path = p;

        String listing = fs.listCurrentDir(path, showHidden, showDetails);
        if (listing != null) sender.sendMessage(listing);
        return true;
    }
}
