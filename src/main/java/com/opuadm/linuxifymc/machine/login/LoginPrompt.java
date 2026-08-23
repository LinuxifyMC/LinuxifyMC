package com.opuadm.linuxifymc.machine.login;

import com.opuadm.linuxifymc.LinuxifyMC;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoginPrompt implements Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> awaiting = ConcurrentHashMap.newKeySet();

    public LoginPrompt(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static String decorateHostname(String host) {
        int[] cps;
        int index;
        int len;
        StringBuilder builder;

        if (host == null || host.isEmpty()) return host;
        cps = host.codePoints().toArray();
        len = cps.length;
        cps[0] = Character.toUpperCase(cps[0]);
        if (len >= 2) {
            cps[len - 2] = Character.toUpperCase(cps[len - 2]);
            cps[len - 1] = Character.toUpperCase(cps[len - 1]);
        }
        builder = new StringBuilder();
        index = 0;
        while (index < cps.length) {
            builder.appendCodePoint(cps[index]);
            index++;
        }
        return builder.toString();
    }

    public void prompt(Player player) {
        UUID uuid;

        if (player == null) return;
        uuid = player.getUniqueId();
        if (Login.isLoggedIn(uuid)) return;

        awaiting.add(uuid);
        player.sendMessage(decorateHostname(LinuxifyMC.hostname) + " " + LinuxifyMC.version);
        player.sendMessage("Login:");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (awaiting.remove(uuid)) {
                player.sendMessage("Login timed out.");
            }
        }, 20L * 30);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player;
        String messageText;
        UUID uuid;

        player = event.getPlayer();
        uuid = player.getUniqueId();

        if (!awaiting.contains(uuid)) {
            if (Login.isLoggedIn(uuid)) awaiting.remove(uuid);
            return;
        }

        event.renderer((source, sourceDisplayName, message, viewer) -> Component.empty());

        messageText = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (messageText.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("Empty username. Try again."));
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            com.opuadm.linuxifymc.Database database;
            Login login;
            Object disabledResult;
            Object existsResult;
            boolean userDisabled;
            boolean userExists;

            if (Login.isLoggedIn(uuid)) {
                awaiting.remove(uuid);
                return;
            }

            database = null;
            if (plugin instanceof com.opuadm.linuxifymc.LinuxifyMC) {
                database = ((com.opuadm.linuxifymc.LinuxifyMC) plugin).getDatabase();
            }

            userExists = false;
            userDisabled = false;
            if (database != null) {
                try {
                    existsResult = database.singleValueQuery("SELECT 1 FROM vm_users WHERE player_uuid = ? AND username = ? LIMIT 1",
                            player.getUniqueId().toString(), messageText);
                    userExists = existsResult != null;

                    disabledResult = database.singleValueQuery("SELECT 1 FROM vm_disabled_users WHERE player_uuid = ? AND username = ? LIMIT 1",
                            player.getUniqueId().toString(), messageText);
                    userDisabled = disabledResult != null;
                } catch (Exception ignore) {
                    userExists = false;
                }
            }

            if (!userExists) {
                player.sendMessage("Login failed: user does not exist on this machine. Try again. In-case you don't know the default login, it's literally your minecraft username.");
                player.sendMessage("Login:");
                return;
            }

            if (userDisabled) {
                player.sendMessage("Login failed: user is disabled on this machine.");
                player.sendMessage("Login:");
                return;
            }
            login = new Login(player);
            login.login();

            awaiting.remove(uuid);
            player.sendMessage("Welcome, " + messageText + "!");
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid;

        uuid = event.getPlayer().getUniqueId();
        awaiting.remove(uuid);
        new Login(event.getPlayer()).logout();
    }
}
