package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("unused")
public class Neofetch {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        sender.sendMessage("LinuxifyMC " + LinuxifyMC.version);
        sender.sendMessage(LinuxifyMC.kernelname + " " + LinuxifyMC.kernelver);
        sender.sendMessage(LinuxifyMC.shellname + " " + LinuxifyMC.shellver + " (Minecraft)");
        sender.sendMessage("Current Player: " + player.getName());
        return true;
    }
}
