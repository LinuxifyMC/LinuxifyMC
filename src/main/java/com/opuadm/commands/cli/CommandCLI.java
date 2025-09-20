// General java class for the CLI command. Expect literally everything here to be for the CLI command.
package com.opuadm.commands.cli;

import com.opuadm.LinuxifyMC;
import com.opuadm.machine.fs.FakeFS;
import com.opuadm.machine.fs.ConvertPerms;

import net.kyori.adventure.text.Component;

import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.command.TabCompleter;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.xml.crypto.Data;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class CommandCLI implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            FakeFS fs = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());

            if (args.length == 0) {
                assert fs != null;
                String currentDir = fs.getCurrentDir();
                String prompt = player.getName() + "@" + LinuxifyMC.hostname + ":" + currentDir + "$ ";
                sender.sendMessage(prompt);

                if (!fs.saveFS(player, fs)) {
                    sender.sendMessage("Warning: Failed to save filesystem state");
                }
                return true;
            }

            return executeCommand(sender, player, fs, args);
        }
        return true;
    }

    public boolean executeCommand(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args.length == 0) return true;

        String currentDir = fs.getCurrentDir();
        String prompt = player.getName().toLowerCase() + "@" + LinuxifyMC.hostname + ":" + currentDir + "$ ";
        String fullCommand = String.join(" ", args);

        boolean isRedirection = false;
        boolean isAppending = false;
        String redirectFile = null;
        StringBuilder capturedOutput = new StringBuilder();

        if (fullCommand.contains(" > ") || fullCommand.contains(" >> ")) {
            isRedirection = true;
            isAppending = fullCommand.contains(" >> ");
            String[] parts = isAppending ? fullCommand.split(" >> ", 2) : fullCommand.split(" > ", 2);
            fullCommand = parts[0].trim();
            redirectFile = parts[1].trim();
            if (redirectFile.startsWith("~")) redirectFile = redirectFile.replaceFirst("~", "/home/" + player.getName());
            args = fullCommand.split("\\s+");
        }

        boolean success = false;
        sender.sendMessage(prompt + fullCommand);
        CommandSender effectiveSender = sender;

        if (isRedirection) {
            final StringBuilder output = capturedOutput;
            effectiveSender = new CommandSender() {
                public void sendMessage(String message) { output.append(message).append("\n"); }
                public void sendMessage(String[] messages) { for (String msg : messages) sendMessage(msg); }
                public void sendMessage(UUID uuid, String message) { if (player.getUniqueId().equals(uuid)) sendMessage(message); }
                public void sendMessage(UUID uuid, String... messages) { if (player.getUniqueId().equals(uuid)) sendMessage(messages); }
                public @NotNull Component name() { return Component.text(player.getName()); }
                public String getName() { return player.getName(); }
                public Server getServer() { return sender.getServer(); }
                public Spigot spigot() { return sender.spigot(); }
                public boolean isPermissionSet(String name) { return player.isPermissionSet(name); }
                public boolean isPermissionSet(Permission perm) { return player.isPermissionSet(perm); }
                public boolean hasPermission(String name) { return player.hasPermission(name); }
                public boolean hasPermission(Permission perm) { return player.hasPermission(perm); }
                public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return player.addAttachment(plugin, name, value); }
                public PermissionAttachment addAttachment(Plugin plugin) { return player.addAttachment(plugin); }
                public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return player.addAttachment(plugin, name, value, ticks); }
                public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return player.addAttachment(plugin, ticks); }
                public void removeAttachment(PermissionAttachment attachment) { player.removeAttachment(attachment); }
                public void recalculatePermissions() { player.recalculatePermissions(); }
                public Set<PermissionAttachmentInfo> getEffectivePermissions() { return player.getEffectivePermissions(); }
                public boolean isOp() { return player.isOp(); }
                public void setOp(boolean value) { player.setOp(value); }
            };
        }

        if (fullCommand.contains("&&")) {
            String[] chainedCommands = fullCommand.split("\\s*&&\\s*");
            success = true;
            for (String cmd : chainedCommands) {
                String[] cmdArgs = cmd.trim().split("\\s+");
                if (cmdArgs.length == 0) continue;
                String cmdName = cmdArgs[0];
                if (!Arrays.asList(CommandVarsCLI.cmds).contains(cmdName)) {
                    effectiveSender.sendMessage(cmdName + ": command not found");
                    success = false;
                    break;
                }
                try {
                    Class<?> commandClass;
                    try {
                        commandClass = Class.forName("com.opuadm.commands.cli.cmds." + cmdName.substring(0, 1).toUpperCase() + cmdName.substring(1).toLowerCase());
                    } catch (ClassNotFoundException e1) {
                        try {
                            commandClass = Class.forName("com.opuadm.commands.cli.cmds." + cmdName.toUpperCase());
                        } catch (ClassNotFoundException e2) {
                            commandClass = Class.forName("com.opuadm.commands.cli.cmds." + cmdName.toLowerCase());
                        }
                    }
                    Object commandInstance = commandClass.getDeclaredConstructor().newInstance();
                    Method executeMethod = commandClass.getMethod("execute", CommandSender.class, Player.class, FakeFS.class, String[].class);
                    if (!(boolean)executeMethod.invoke(commandInstance, effectiveSender, player, fs, cmdArgs)) {
                        success = false;
                        break;
                    }
                } catch (ClassNotFoundException e) {
                    effectiveSender.sendMessage(cmdName + ": implementation not found");
                    success = false;
                    break;
                } catch (Exception e) {
                    effectiveSender.sendMessage(cmdName + ": error: " + e.getMessage());
                    Logger.getLogger("LinuxifyMC").log(Level.SEVERE, "Error executing command " + cmdName, e);
                    success = false;
                    break;
                }
            }
        } else {
            String cmdName = args[0];
            if (!Arrays.asList(CommandVarsCLI.cmds).contains(cmdName)) {
                effectiveSender.sendMessage(cmdName + ": command not found");
                return false;
            }
            try {
                Class<?> commandClass;
                try {
                    commandClass = Class.forName("com.opuadm.commands.cli.cmds." + cmdName.substring(0, 1).toUpperCase() + cmdName.substring(1).toLowerCase());
                } catch (ClassNotFoundException e1) {
                    try {
                        commandClass = Class.forName("com.opuadm.commands.cli.cmds." + cmdName.toUpperCase());
                    } catch (ClassNotFoundException e2) {
                        commandClass = Class.forName("com.opuadm.commands.cli.cmds." + cmdName.toLowerCase());
                    }
                }
                Object commandInstance = commandClass.getDeclaredConstructor().newInstance();
                Method executeMethod = commandClass.getMethod("execute", CommandSender.class, Player.class, FakeFS.class, String[].class);
                success = (boolean)executeMethod.invoke(commandInstance, effectiveSender, player, fs, args);
            } catch (ClassNotFoundException e) {
                effectiveSender.sendMessage(cmdName + ": implementation not found");
            } catch (Exception e) {
                effectiveSender.sendMessage(cmdName + ": error: " + e.getMessage());
                Logger.getLogger("LinuxifyMC").log(Level.SEVERE, "Error executing command " + cmdName, e);
            }
        }

        if (isRedirection) {
            String content = capturedOutput.toString();
            if (isAppending) fs.appendFile(redirectFile, content);
            else
                fs.makeFile(redirectFile, player.getName(), ConvertPerms.symbolicToOctal("777"), content);
        }

        return success;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        List<String> availableCommands = new ArrayList<>();
        for (String cmd : CommandVarsCLI.cmds) {
            if (cmd.equals("test") || cmd.equals("serverfetch")) {
                if (sender.hasPermission("linuxifymc.command.cli.nonlinuxcmds")) {
                    availableCommands.add(cmd);
                }
            } else {
                availableCommands.add(cmd);
            }
        }

        String fullInput = String.join(" ", args);
        if (fullInput.contains("&&")) {
            String[] parts = fullInput.split("&&");
            String lastPart = parts[parts.length - 1].trim();

            if (lastPart.isEmpty() && fullInput.endsWith("&&")) {
                StringUtil.copyPartialMatches("", availableCommands, completions);
                return completions;
            }

            String[] chainArgs = lastPart.split("\\s+");

            if (chainArgs.length == 1) {
                StringUtil.copyPartialMatches(chainArgs[0], availableCommands, completions);
            } else {
                String cmdName = chainArgs[0];
                String argToComplete = chainArgs[chainArgs.length - 1];

                if (cmdName.equalsIgnoreCase("ls") && chainArgs.length == 2) {
                    StringUtil.copyPartialMatches(argToComplete, CommandVarsCLI.LsOpts(), completions);
                } else if (cmdName.equalsIgnoreCase("uname") && chainArgs.length == 2) {
                    StringUtil.copyPartialMatches(argToComplete, CommandVarsCLI.UnameOpts(), completions);
                } else if (cmdName.equalsIgnoreCase("chmod") && chainArgs.length == 2) {
                    StringUtil.copyPartialMatches(argToComplete, CommandVarsCLI.ChmodPerms(), completions);
                } else if (cmdName.equalsIgnoreCase("uname") && chainArgs.length == 3 && chainArgs[1].equalsIgnoreCase("-s")) {
                    StringUtil.copyPartialMatches(argToComplete, CommandVarsCLI.UnameOptsS(), completions);
                }
            }
        } else {
            switch (args.length) {
                case 1:
                    StringUtil.copyPartialMatches(args[0], availableCommands, completions);
                    break;
                case 2:
                    if (args[0].equalsIgnoreCase("ls")) {
                        StringUtil.copyPartialMatches(args[1], CommandVarsCLI.LsOpts(), completions);
                    } else if (args[0].equalsIgnoreCase("uname")) {
                        StringUtil.copyPartialMatches(args[1], CommandVarsCLI.UnameOpts(), completions);
                    } else if (args[0].equalsIgnoreCase("chmod")) {
                        StringUtil.copyPartialMatches(args[1], CommandVarsCLI.ChmodPerms(), completions);
                    } else if (args[0].equalsIgnoreCase("mkdir")) {
                        StringUtil.copyPartialMatches(args[1], CommandVarsCLI.MkdirOpts(), completions);
                    }
                    break;
                case 3:
                    if (args[0].equalsIgnoreCase("uname") && args[1].equalsIgnoreCase("-s")) {
                        StringUtil.copyPartialMatches(args[2], CommandVarsCLI.UnameOptsS(), completions);
                    }
                    break;
            }
        }

        return completions;
    }
}