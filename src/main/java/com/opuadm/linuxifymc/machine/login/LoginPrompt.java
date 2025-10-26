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
        if (host == null || host.isEmpty()) return host;
        int[] cps = host.codePoints().toArray();
        int len = cps.length;
        cps[0] = Character.toUpperCase(cps[0]);
        if (len >= 2) {
            cps[len - 2] = Character.toUpperCase(cps[len - 2]);
            cps[len - 1] = Character.toUpperCase(cps[len - 1]);
        }
        StringBuilder sb = new StringBuilder();
        for (int cp : cps) sb.appendCodePoint(cp);
        return sb.toString();
    }

    public void prompt(Player player) {
        if (player == null) return;
        UUID u = player.getUniqueId();
        if (Login.isLoggedIn(u)) return;

        awaiting.add(u);
        player.sendMessage(decorateHostname(LinuxifyMC.hostname) + " " + LinuxifyMC.version);
        player.sendMessage("Login:");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (awaiting.remove(u)) {
                player.sendMessage("Login timed out.");
            }
        }, 20L * 30);
    }

    @EventHandler
    public void onChat(AsyncChatEvent ev) {
        Player player = ev.getPlayer();
        UUID u = player.getUniqueId();

        if (!awaiting.contains(u)) {
            if (Login.isLoggedIn(u)) awaiting.remove(u);
            return;
        }

        ev.renderer((source, sourceDisplayName, message, viewer) -> Component.empty());

        String messageText = PlainTextComponentSerializer.plainText().serialize(ev.message()).trim();
        if (messageText.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("Empty username. Try again."));
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (Login.isLoggedIn(u)) {
                awaiting.remove(u);
                return;
            }

            com.opuadm.linuxifymc.Database db = null;
            if (plugin instanceof com.opuadm.linuxifymc.LinuxifyMC l) {
                db = l.getDatabase();
            }

            boolean userExists = false;
            boolean userDisabled = false;
            if (db != null) {
                try {
                    Object existsObj = db.singleValueQuery("SELECT 1 FROM vm_users WHERE player_uuid = ? AND username = ? LIMIT 1",
                            player.getUniqueId().toString(), messageText);
                    userExists = existsObj != null;

                    Object disabledObj = db.singleValueQuery("SELECT 1 FROM vm_disabled_users WHERE player_uuid = ? AND username = ? LIMIT 1",
                            player.getUniqueId().toString(), messageText);
                    userDisabled = disabledObj != null;
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
            Login login = new Login(player);
            login.login();

            awaiting.remove(u);
            player.sendMessage("Welcome, " + messageText + "!");
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        UUID u = ev.getPlayer().getUniqueId();
        awaiting.remove(u);
        new Login(ev.getPlayer()).logout();
    }
}