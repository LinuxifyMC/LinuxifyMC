package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.commands.cli.CliCommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

public class CD implements CliCommand {
    @Override
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String newPath = ArgUtils.getPositional(args, 1);
        if (newPath != null) {
            try {
                fs.getDir(newPath);
                if (!fs.setCurrentDir(newPath)) {
                    sender.sendMessage("cd: cannot change directory to '" + newPath + "'");
                }
            } catch (Exception e) {
                sender.sendMessage("cd: cannot access '" + newPath + "': No such file or directory");
            }
        } else {
            sender.sendMessage("Current directory: " + fs.getCurrentDir());
        }
        return true;
    }
}
