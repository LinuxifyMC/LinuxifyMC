package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class Test {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        sender.sendMessage("This is a test command (Output Test)");
        return true;
    }
}