package com.opuadm.commands.linuxifymc;

import com.opuadm.LinuxifyMC;
import com.opuadm.machine.states.Power;

import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class VM implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("LinuifyMC Virtual Computer Management for LinuxifyMC " + LinuxifyMC.version);
            sender.sendMessage("Syntax: /vcomputer <start|stop>");
            return true;
        } else if (args.length == 1 && args[0].equals("start")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("E: Only players can start a virtual computer. Are you executing this command from the console?");
                return true;
            }
            Power.getFor(player).TurnOn();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("start");
            completions.add("stop");
        }

        return completions;
    }
}