// Settings
package com.opuadm.commands.linuxifymc;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

import com.opuadm.LinuxifyMC;

public class Settings implements CommandExecutor, TabCompleter {
    public static String perm1 = "linuxifymc.command.cli.nonlinuxcmds";
    public static String perm2 = "linuxifymc.command.bootlogs.sendinchat";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("LinuifyMC Settings for LinuxifyMC " + LinuxifyMC.version);
            sender.sendMessage("Syntax: /linuxifymc <global|user> <set|get> <setting> [value] [--no-override]");
            sender.sendMessage("Available Settings:");
            sender.sendMessage("non-linux-commands <true|false>, default: true | Enable or disable non-linux commands.");
            return true;
        }

        if (args[0].equalsIgnoreCase("global")) {
            if (!sender.hasPermission("linuxifymc.command.settings.global")) {
                sender.sendMessage("E: You do not have permission to change global settings.");
                return true;
            }

            if (args.length >= 3 && args[1].equalsIgnoreCase("get")) {
                String settingName = args[2];
                if (settingName.equalsIgnoreCase("non-linux-commands")) {
                    Permission permission = Bukkit.getPluginManager().getPermission(perm1);
                    boolean value = false;
                    if (permission != null) {
                        value = permission.getDefault() == PermissionDefault.TRUE;
                    }
                    sender.sendMessage("Global setting non-linux-commands: " + value);
                } else {
                    sender.sendMessage("E: Unknown global setting: " + settingName);
                }
                return true;
            }

            if (args.length >= 4 && args[1].equalsIgnoreCase("set")) {
                String settingName = args[2];
                String settingValue = args[3];
                boolean noOverride = args.length > 4 && args[4].equalsIgnoreCase("--no-override");

                if (settingName.equalsIgnoreCase("non-linux-commands")) {
                    if (!settingValue.equalsIgnoreCase("true") && !settingValue.equalsIgnoreCase("false")) {
                        sender.sendMessage("E: Invalid value for non-linux-commands. Use true or false.");
                        return true;
                    }

                    boolean newValue = settingValue.equalsIgnoreCase("true");
                    LinuxifyMC plugin = JavaPlugin.getPlugin(LinuxifyMC.class);
                    FileConfiguration config = plugin.getConfig();
                    config.set("settings.global.non-linux-commands", newValue);
                    plugin.saveConfig();

                    Permission permission = Bukkit.getPluginManager().getPermission(perm1);
                    if (permission != null) {
                        permission.setDefault(newValue ? PermissionDefault.TRUE : PermissionDefault.FALSE);
                        Bukkit.getPluginManager().recalculatePermissionDefaults(permission);
                    } else {
                        permission = new Permission(perm1);
                        permission.setDefault(newValue ? PermissionDefault.TRUE : PermissionDefault.FALSE);
                        Bukkit.getPluginManager().addPermission(permission);
                    }

                    if (!noOverride) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            PermissionAttachment attachment = player.addAttachment(plugin);
                            attachment.setPermission(perm1, newValue);
                        }
                        sender.sendMessage("Global non-linux-commands has been set to " + newValue);
                    } else {
                        sender.sendMessage("Global non-linux-commands has been set to " + newValue + " (not overriding user settings)");
                    }
                } else {
                    sender.sendMessage("E: Unknown global setting: " + settingName);
                }
                return true;
            }
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

                if (settingName.equalsIgnoreCase("non-linux-commands")) {
                    boolean value = player.hasPermission(perm1);
                    sender.sendMessage("Your setting non-linux-commands value is currently set to " + value);
                } else if (settingName.equalsIgnoreCase("boot-logs-in-chat")) {
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
                boolean noOverride = args.length > 4 && args[4].equalsIgnoreCase("--no-override");

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("E: Only players can use this command");
                    return true;
                }

                if (settingName.equalsIgnoreCase("non-linux-commands")) {
                    if (!settingValue.equalsIgnoreCase("true") && !settingValue.equalsIgnoreCase("false")) {
                        sender.sendMessage("E: Invalid value for non-linux-commands. Use true or false.");
                        return true;
                    }

                    boolean newValue = settingValue.equalsIgnoreCase("true");

                    Permission permission = Bukkit.getPluginManager().getPermission(perm1);
                    if (!noOverride && permission != null &&
                            (permission.getDefault() == PermissionDefault.TRUE ||
                                    permission.getDefault() == PermissionDefault.FALSE)) {
                        sender.sendMessage("W: Note that global settings will override this user setting unless --no-override is used.");
                    }

                    PermissionAttachment attachment = player.addAttachment(JavaPlugin.getPlugin(LinuxifyMC.class));
                    attachment.setPermission(perm1, newValue);

                    sender.sendMessage("Your non-linux-commands setting value has been set to " + newValue);
                } else if (settingName.equalsIgnoreCase("boot-logs-in-chat")) {
                    if (!settingValue.equalsIgnoreCase("true") && !settingValue.equalsIgnoreCase("false")) {
                        sender.sendMessage("E: Invalid value for non-linux-commands. Use true or false.");
                        return true;
                    }

                    boolean newValue = settingValue.equalsIgnoreCase("true");

                    Permission permission = Bukkit.getPluginManager().getPermission(perm1);
                    if (!noOverride && permission != null &&
                            (permission.getDefault() == PermissionDefault.TRUE ||
                                    permission.getDefault() == PermissionDefault.FALSE)) {
                        sender.sendMessage("W: Note that global settings will override this user setting unless --no-override is used.");
                    }

                    PermissionAttachment attachment = player.addAttachment(JavaPlugin.getPlugin(LinuxifyMC.class));
                    attachment.setPermission(perm1, newValue);

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
            completions.add("global");
            completions.add("user");
        } else if (args.length == 2) {
            completions.add("set");
            completions.add("get");
        } else if (args.length == 3) {
            if ((args[0].equalsIgnoreCase("global") || args[0].equalsIgnoreCase("user")) &&
                    (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("get"))) {
                completions.add("non-linux-commands");
                completions.add("boot-logs-in-chat");
            }
        } else if (args.length == 4) {
            if ((args[0].equalsIgnoreCase("global") || args[0].equalsIgnoreCase("user")) &&
                    args[1].equalsIgnoreCase("set")) {
                completions.add("true");
                completions.add("false");
            }
        } else if (args.length == 5) {
            if ((args[0].equalsIgnoreCase("global") || args[0].equalsIgnoreCase("user")) &&
                    args[1].equalsIgnoreCase("set")) {
                completions.add("--no-override");
            }
        }

        return completions;
    }
}