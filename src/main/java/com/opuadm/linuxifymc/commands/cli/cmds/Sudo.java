package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.shell.SudoContext;
import com.opuadm.linuxifymc.machine.shell.Shell;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sudo {
    private static final Logger LOG = Logger.getLogger("LinuxifyMC");

    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args == null || args.length <= 1) {
            sender.sendMessage("usage: sudo [-u user] command [arg ...]");
            return false;
        }

        if (!sender.hasPermission("linuxifymc.command.sudo")) {
            sender.sendMessage("sudo: permission denied");
            return false;
        }

        String cmdName = ArgUtils.getPositional(args, 1);
        if (cmdName == null) {
            sender.sendMessage("usage: sudo [-u user] command [arg ...]");
            return false;
        }
        cmdName = cmdName.trim().toLowerCase();

        String[] available = com.opuadm.linuxifymc.machine.shell.ShellVars.cmds;
        if (!Arrays.asList(available).contains(cmdName)) {
            sender.sendMessage("sudo: command not found: " + cmdName);
            return false;
        }

        List<String> sub = new ArrayList<>();
        sub.add(cmdName);
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;
            if ("-u".equals(a)) {
                i++;
                continue;
            }
            if (a.equalsIgnoreCase(cmdName)) continue;
            sub.add(a);
        }

        try {
            String CMDS_PKG = "com.opuadm.linuxifymc.commands.cli.cmds.";
            String cap = cmdName.substring(0, 1).toUpperCase() + cmdName.substring(1).toLowerCase();
            String[] candidates = {CMDS_PKG + cap, CMDS_PKG + cmdName.toUpperCase(), CMDS_PKG + cmdName.toLowerCase()};
            Class<?> clazz = null;
            ClassNotFoundException lastEx = null;
            for (String c : candidates) {
                try { clazz = Class.forName(c); break; }
                catch (ClassNotFoundException e) { lastEx = e; }
            }
            if (clazz == null) {
                assert lastEx != null;
                throw lastEx;
            }

            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method m = clazz.getMethod("execute", CommandSender.class, Player.class, FakeFS.class, String[].class);
            CommandSender elevated = new Shell.ElevatedSender(sender, player);

            SudoContext.enter();
            try {
                return (boolean) m.invoke(instance, elevated, player, fs, sub.toArray(new String[0]));
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
}