package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.login.Login;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class Su {
    private static final Map<UUID, String> previous = new ConcurrentHashMap<>();

    public static String getPreviousUser(UUID uuid) {
        return previous.get(uuid);
    }

    public static void removePreviousUser(UUID uuid) {
        previous.remove(uuid);
    }

    public static boolean run(Player player, String[] args, FakeFS fs) {
        if (player == null) return false;

        String target;
        if (args == null || args.length == 0) {
            target = "root";
        } else {
            String a0 = args[0].trim();
            if (a0.equals("-") || a0.isEmpty()) {
                target = "root";
            } else {
                target = a0;
            }
        }

        LinuxifyMC plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(LinuxifyMC.class);
        com.opuadm.linuxifymc.Database dbObj = plugin.getDatabase();

        if (!"root".equals(target)) {
            boolean exists = false;
            if (dbObj != null) {
                try {
                    Object res = dbObj.singleValueQuery(
                            "SELECT 1 FROM vm_users WHERE player_uuid = ? AND username = ? LIMIT 1",
                            player.getUniqueId().toString(), target);
                    exists = res != null;
                } catch (Exception ignored) {
                }
            }
            if (!exists) {
                player.sendMessage("su: user " + target + " does not exist");
                return false;
            }
        }

        try {
            Login session = new Login(new String[] { player.getName(), target, "root" });
            String[] us = session.getUsers();
            int chosenIndex = 0;
            for (int i = 0; i < us.length; i++) {
                if (us[i].equals(target)) {
                    chosenIndex = i;
                    break;
                }
            }
            session.setUsers(new String[] { player.getName(), target, "root" });
            session.setCurrentUserIndex(chosenIndex);

            Login.setSession(player.getUniqueId(), session);

            previous.put(player.getUniqueId(), player.getName());

            if ("root".equalsIgnoreCase(target)) {
                com.opuadm.linuxifymc.machine.shell.SudoContext.enter();
            }

            if (fs != null) {
                String cur = fs.getCurrentDir();
                String home = "/home/" + target;
                if ("/".equals(cur) || cur.startsWith("/home/" + player.getName())) {
                    try {
                        fs.setCurrentDir(home);
                    } catch (Exception ignore) {
                    }
                }
            }

            player.sendMessage("Password:");
            player.sendMessage("Switched to user " + target);
            return true;
        } catch (Exception e) {
            player.sendMessage("su: failed to switch user: " + e.getMessage());
            return false;
        }
    }

    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (!(sender instanceof Player) || player == null) return false;
        String[] forwarded = ArgUtils.argsAfterCommand(args);
        return run(player, forwarded, fs);
    }
}