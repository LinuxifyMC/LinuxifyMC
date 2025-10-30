package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.login.Login;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.UUID;

public class Exit {
    public static boolean run(Player player, FakeFS fs) {
        if (player == null) return false;

        try {
            UUID u = player.getUniqueId();
            String prev = Su.getPreviousUser(u);

            if (prev != null) {
                Login restored = new Login(new String[] { prev, "root" });
                restored.setUsers(new String[] { prev, "root" });
                int idx = 0;
                String[] us = restored.getUsers();
                for (int i = 0; i < us.length; i++) {
                    if (us[i].equals(prev)) {
                        idx = i;
                        break;
                    }
                }
                restored.setCurrentUserIndex(idx);

                Login.setSession(u, restored);
                Su.removePreviousUser(u);

                if (fs != null) {
                    try {
                        fs.setCurrentDir("/home/" + prev);
                    } catch (Exception ignore) {}
                }

                player.sendMessage("Exiting to " + prev + ".");
                return true;
            }

            Login.setSession(u, null);

            if (fs != null) {
                fs.saveFS(player, fs);
            }

            LinuxifyMC l = JavaPlugin.getPlugin(LinuxifyMC.class);
            l.getLoginPrompt().prompt(player);
            return true;
        } catch (Exception e) {
            player.sendMessage("exit: error: " + e.getMessage());
            return false;
        }
    }

    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (!(sender instanceof Player) || player == null) return false;
        String[] forwarded;
        if (args == null || args.length <= 1) {
            forwarded = new String[0];
        } else {
            forwarded = Arrays.copyOfRange(args, 1, args.length);
        }
        if (forwarded.length > 0) {
            try {
                Integer.parseInt(forwarded[0]);
            } catch (Exception ignore) {}
        }
        return run(player, fs);
    }
}