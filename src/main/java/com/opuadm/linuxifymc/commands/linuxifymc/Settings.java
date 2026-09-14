package com.opuadm.linuxifymc.commands.linuxifymc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

import com.opuadm.linuxifymc.LinuxifyMC;

public class Settings implements CommandExecutor, TabCompleter {
    public static String perm2 = "linuxifymc.command.bootlogs.sendinchat";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(LinuxifyMC.pluginName + " Settings for " + LinuxifyMC.pluginName + " " + LinuxifyMC.version);
            sender.sendMessage("Syntax: /linuxifymc <user> <set|get> <setting> [value]");
            sender.sendMessage("Available Settings:");
            sender.sendMessage("boot-logs-in-chat <true|false>, default: true | Send boot logs in chat.");
            return true;
        }

        if (args[0].equalsIgnoreCase("user")) {
            if (!sender.hasPermission("linuxifymc.command.settings.user")) {
                sender.sendMessage("E: You do not have permission to change user settings.");
                return true;
            }

            if (args.length >= 3 && args[1].equalsIgnoreCase("get")) {
                String settingName = args[2];
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("E: Only players can use this command");
                    return true;
                }

                if (settingName.equalsIgnoreCase("boot-logs-in-chat")) {
                    boolean value = player.hasPermission(perm2);
                    sender.sendMessage("Your setting boot-logs-in-chat value is currently set to " + value);
                } else {
                    sender.sendMessage("E: Unknown user setting: " + settingName);
                }

                return true;
            }

            if (args.length >= 4 && args[1].equalsIgnoreCase("set")) {
                String settingName = args[2];
                String settingValue = args[3];

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("E: Only players can use this command");
                    return true;
                }

                if (settingName.equalsIgnoreCase("boot-logs-in-chat")) {
                    if (!settingValue.equalsIgnoreCase("true") && !settingValue.equalsIgnoreCase("false")) {
                        sender.sendMessage("E: Invalid value for boot-logs-in-chat. Use true or false.");
                        return true;
                    }

                    boolean newValue = settingValue.equalsIgnoreCase("true");

                    PermissionAttachment attachment = player.addAttachment(JavaPlugin.getPlugin(LinuxifyMC.class));
                    attachment.setPermission(perm2, newValue);

                    sender.sendMessage("Your boot-logs-in-chat setting value has been set to " + newValue);
                } else {
                    sender.sendMessage("E: Unknown user setting: " + settingName);
                }
                return true;
            }
        }

        sender.sendMessage("E: Invalid command syntax. Use /linuxifymc for help.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("user");
        } else if (args.length == 2) {
            completions.add("set");
            completions.add("get");
        } else if (args.length == 3) {
            if ((args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("get")) &&
                    args[0].equalsIgnoreCase("user")) {
                completions.add("boot-logs-in-chat");
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("user") &&
                    args[1].equalsIgnoreCase("set")) {
                completions.add("true");
                completions.add("false");
            }
        }

        return completions;
    }
}
