package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.Database;
import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.shell.SudoContext;
import com.opuadm.linuxifymc.machine.shell.Shell;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sudo {
    private static final Logger LOG = Logger.getLogger(LinuxifyMC.pluginName);

    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String targetUser;
        String cmdName;
        int commandIndex;
        int argumentIndex;
        List<String> sub;
        Class<?> clazz;
        Object instance;
        Method method;
        CommandSender elevated;

        if (args == null || args.length <= 1) {
            sender.sendMessage("usage: sudo [-u user] command [arg ...]");
            return false;
        }

        if (!sender.hasPermission("linuxifymc.command.sudo")) {
            sender.sendMessage("sudo: permission denied");
            return false;
        }

        targetUser = "root";
        commandIndex = 1;
        if ("-u".equals(args[commandIndex])) {
            if (args.length <= commandIndex + 2) {
                sender.sendMessage("usage: sudo [-u user] command [arg ...]");
                return false;
            }
            targetUser = args[commandIndex + 1];
            commandIndex += 2;
        }

        cmdName = args[commandIndex];
        cmdName = cmdName.trim().toLowerCase();

        if (!userExists(player, targetUser)) {
            sender.sendMessage("sudo: unknown user: " + targetUser);
            return false;
        }

        if (!Arrays.asList(com.opuadm.linuxifymc.machine.shell.ShellVars.cmds).contains(cmdName)) {
            sender.sendMessage("sudo: command not found: " + cmdName);
            return false;
        }

        sub = new ArrayList<>();
        for (argumentIndex = commandIndex; argumentIndex < args.length; argumentIndex++) {
            sub.add(args[argumentIndex]);
        }

        try {
            clazz = resolveCommandClass(cmdName);
            instance = clazz.getDeclaredConstructor().newInstance();
            method = clazz.getMethod("execute", CommandSender.class, Player.class, FakeFS.class, String[].class);
            elevated = new Shell.ElevatedSender(sender, player);

            SudoContext.enter(player.getUniqueId(), targetUser);
            try {
                return (boolean) method.invoke(instance, elevated, player, fs, sub.toArray(new String[0]));
            } finally {
                SudoContext.exit();
            }
        } catch (ClassNotFoundException e) {
            sender.sendMessage("sudo: command not found: " + cmdName);
            return false;
        } catch (Exception e) {
            sender.sendMessage("sudo: error: " + e.getMessage());
            LOG.log(Level.SEVERE, "Error executing sudo target " + cmdName, e);
            return false;
        }
    }

    private static Class<?> resolveCommandClass(String cmdName) throws ClassNotFoundException {
        String packageName;
        String capitalized;
        String[] candidates;
        int candidateIndex;

        packageName = "com.opuadm.linuxifymc.commands.cli.cmds.";
        capitalized = cmdName.substring(0, 1).toUpperCase() + cmdName.substring(1).toLowerCase();
        candidates = new String[] {
                packageName + capitalized,
                packageName + cmdName.toUpperCase(),
                packageName + cmdName.toLowerCase()
        };
        for (candidateIndex = 0; candidateIndex < candidates.length; candidateIndex++) {
            try {
                return Class.forName(candidates[candidateIndex]);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(cmdName);
    }

    private static boolean userExists(Player player, String username) {
        LinuxifyMC plugin;
        Database database;
        Object result;

        if ("root".equalsIgnoreCase(username)) return true;
        plugin = JavaPlugin.getPlugin(LinuxifyMC.class);
        database = plugin.getDatabase();
        if (database == null) return false;
        try {
            result = database.singleValueQuery(
                    "SELECT 1 FROM vm_users WHERE player_uuid = ? AND username = ? LIMIT 1",
                    player.getUniqueId().toString(), username);
            return result != null;
        } catch (Exception exception) {
            LOG.log(Level.WARNING, "Failed to resolve sudo user " + username, exception);
            return false;
        }
    }
}
