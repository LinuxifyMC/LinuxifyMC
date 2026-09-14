package com.opuadm.linuxifymc.commands.cli;

import com.opuadm.linuxifymc.machine.fs.FakeFS;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface CliCommand {
    boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args);
}
