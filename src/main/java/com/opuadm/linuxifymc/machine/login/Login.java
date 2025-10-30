package com.opuadm.linuxifymc.machine.login;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Login {
    private final Player player;
    private String[] users;
    private int currentUserIndex;

    private static final Map<UUID, Login> sessions = new ConcurrentHashMap<>();

    public Login(Player player) {
        this.player = player;
        if (player != null) {
            this.users = new String[] { player.getName(), "root" };
        } else {
            this.users = new String[] { "root" };
        }
        this.currentUserIndex = 0;
    }

    public Login(String[] users) {
        this.player = null;
        this.users = (users != null) ? users.clone() : new String[] { "root" };
        this.currentUserIndex = 0;
    }

    public void login() {
        if (player != null) {
            String name = player.getName();
            int idx = -1;
            for (int i = 0; i < users.length; i++) {
                if (users[i].equals(name)) {
                    idx = i;
                    break;
                }
            }
            currentUserIndex = Math.max(idx, 0);

            sessions.put(player.getUniqueId(), this);
        } else {
            int idx = -1;
            for (int i = 0; i < users.length; i++) {
                if ("root".equals(users[i])) {
                    idx = i;
                    break;
                }
            }
            currentUserIndex = Math.max(idx, 0);
        }
    }

    public void logout() {
        if (player != null) {
            sessions.remove(player.getUniqueId());
        }
    }

    public static boolean isLoggedIn(UUID uuid) {
        return uuid != null && sessions.containsKey(uuid);
    }

    public String[] getUsers() {
        return users.clone();
    }

    public String getCurrentUser() {
        if (currentUserIndex < 0 || currentUserIndex >= users.length) {
            return null;
        }
        return users[currentUserIndex];
    }

    public static Login getSession(UUID uuid) {
        if (uuid == null) return null;
        return sessions.get(uuid);
    }

    public static void setSession(UUID uuid, Login login) {
        if (uuid == null) return;
        if (login == null) {
            sessions.remove(uuid);
        } else {
            sessions.put(uuid, login);
        }
    }

    public void setUsers(String[] users) {
        this.users = (users != null) ? users.clone() : new String[] { "root" };
    }

    public void setCurrentUserIndex(int idx) {
        this.currentUserIndex = idx;
    }
}